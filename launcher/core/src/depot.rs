//! The shared, content-addressed store every instance draws from.
//!
//! Layout follows Mojang's own, so a depot is legible to anyone who has seen
//! a `.minecraft` folder:
//!
//! ```text
//! depot/
//! ├── meta/version_manifest_v2.json
//! ├── versions/<id>/<id>.json
//! ├── versions/<id>/<id>.jar
//! ├── versions/<id>/natives/          unpacked, for versions that need it
//! ├── libraries/<maven path>.jar
//! ├── runtimes/<platform>/<component> the JRE ash downloaded
//! └── assets/indexes/<id>.json
//!     assets/objects/<ab>/<sha1>
//!     assets/log_configs/<id>         Mojang's log4j configuration
//! ```
//!
//! The depot is a cache, never a redistribution point: every byte comes from
//! Mojang's own manifest and CDN, which the EULA requires.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;

use futures::stream::{self, StreamExt};
use serde::Serialize;
use sha1::{Digest, Sha1};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest, HttpResponse};
use crate::natives;
use crate::version::{self, Os};

/// How many downloads run at once.
///
/// An asset index is several thousand small files, so serial downloading is
/// unusably slow; unbounded is worse, because it will exhaust file handles
/// and make Mojang think it is being attacked.
const CONCURRENCY: usize = 8;

/// Which version to prepare, and how to verify its metadata.
///
/// A struct rather than three loose strings: they always travel together and
/// two of them are indistinguishable at a call site.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VersionSource {
    pub id: String,
    pub url: String,
    /// Published hash of the metadata at `url`. May be empty; Mojang has
    /// shipped manifest entries without one.
    pub sha1: String,
}

/// One file the depot needs, and how to know it arrived intact.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Artifact {
    pub url: String,
    pub sha1: String,
    pub size: u64,
    /// Relative to the depot root.
    pub path: String,
}

/// Everything a version target needs, and what is already present.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Plan {
    pub version_id: String,
    /// Which Java runtime this version asks for. `None` on versions whose
    /// metadata predates the field.
    pub java_component: Option<String>,
    pub total_files: usize,
    pub missing_files: usize,
    pub missing_bytes: u64,
    #[serde(skip)]
    pub missing: Vec<Artifact>,
}

/// Progress, as a stream of facts rather than one opaque percentage.
///
/// The UI derives its own display from these. A single float could not say
/// "4,300 of 4,900 files, and the last one failed verification".
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "event", rename_all = "snake_case")]
pub enum PrepareEvent {
    /// Reading version metadata and the asset index.
    Resolving { version_id: String },
    /// The plan is known. `already_present` files need no work.
    Planned { total_files: usize, missing_files: usize, missing_bytes: u64, already_present: usize },
    /// One file finished and verified.
    Downloaded { path: String, bytes: u64, done_files: usize, done_bytes: u64 },
    /// Picking a partly-downloaded file back up rather than restarting it.
    Resuming { path: String, from_bytes: u64 },
    /// Moving on to the Java runtime. A second `Planned` follows for it, so
    /// the UI knows the counters it is about to see belong to a new phase.
    Runtime { component: String },
    /// A file failed its hash and is being fetched again.
    Reverifying { path: String },
    Cancelled,
    Done { version_id: String },
}

/// Where progress goes. Tests record it; the adapter forwards it to the UI.
pub trait ProgressSink: Send + Sync {
    fn emit(&self, event: PrepareEvent);
}

/// A sink that throws progress away, for callers that do not want it.
pub struct NullSink;

impl ProgressSink for NullSink {
    fn emit(&self, _event: PrepareEvent) {}
}

/// Cooperative cancellation. Checked between files and while streaming one.
#[derive(Debug, Clone, Default)]
pub struct Cancel(Arc<AtomicBool>);

impl Cancel {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn cancel(&self) {
        self.0.store(true, Ordering::SeqCst);
    }

    pub fn is_cancelled(&self) -> bool {
        self.0.load(Ordering::SeqCst)
    }
}

// ---- paths -----------------------------------------------------------------

fn version_json_path(id: &str) -> String {
    format!("versions/{id}/{id}.json")
}

fn version_jar_path(id: &str) -> String {
    format!("versions/{id}/{id}.jar")
}

fn asset_index_path(id: &str) -> String {
    format!("assets/indexes/{id}.json")
}

fn library_path(relative: &str) -> String {
    format!("libraries/{relative}")
}

/// Where Mojang's own launcher keeps log4j configurations, so a depot stays
/// legible to anyone who has seen a `.minecraft` folder.
pub(crate) fn log_config_path(id: &str) -> String {
    format!("assets/log_configs/{id}")
}

// ---- verification ----------------------------------------------------------

fn sha1_of(bytes: &[u8]) -> String {
    let mut hasher = Sha1::new();
    hasher.update(bytes);
    hasher.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// Already in the depot and intact?
///
/// Size is checked first because it is free; the hash is only computed when
/// the size matches, which keeps a re-plan over thousands of assets cheap.
fn is_present(depot_root: &Path, artifact: &Artifact) -> bool {
    let path = depot_root.join(&artifact.path);
    let Ok(metadata) = fs::metadata(&path) else {
        return false;
    };
    if metadata.len() != artifact.size {
        return false;
    }
    match fs::read(&path) {
        Ok(bytes) => sha1_of(&bytes) == artifact.sha1,
        Err(_) => false,
    }
}

fn write_atomically(depot_root: &Path, relative: &str, bytes: &[u8]) -> Result<(), AshError> {
    let path = depot_root.join(relative);
    let parent = path.parent().expect("a depot path always has a parent");
    fs::create_dir_all(parent).map_err(|e| AshError::Storage {
        detail: format!("creating {}: {e}", parent.display()),
    })?;

    // Write beside the target and rename, so an interrupted write can never
    // leave a half-file that passes a size check on the next run.
    let temp = path.with_extension("part");
    {
        let mut file = fs::File::create(&temp)
            .map_err(|e| AshError::Storage { detail: format!("creating a temp file: {e}") })?;
        file.write_all(bytes)
            .map_err(|e| AshError::Storage { detail: format!("writing a temp file: {e}") })?;
    }
    fs::rename(&temp, &path)
        .map_err(|e| AshError::Storage { detail: format!("finishing a download: {e}") })
}

// ---- fetching --------------------------------------------------------------

/// Fetch a file the depot needs, verify it, and store it.
///
/// One automatic re-fetch on a hash mismatch, then a typed failure. A
/// corrupted byte stream is usually transient; a second identical failure
/// means something is actually wrong and pretending otherwise would loop.
async fn fetch_verified<S: ProgressSink + ?Sized>(
    http: &dyn HttpPort,
    depot_root: &Path,
    artifact: &Artifact,
    sink: &S,
) -> Result<(), AshError> {
    let final_path = depot_root.join(&artifact.path);
    let part_path = partial_path(depot_root, &artifact.path);

    for attempt in 0..2 {
        // Resume from whatever a previous run managed to write. A `.part`
        // file survives between runs on purpose: a dropped connection
        // partway through a 25MB client jar should not cost the whole file.
        let already = fs::metadata(&part_path).map(|m| m.len()).unwrap_or(0);
        let resuming = already > 0 && already < artifact.size && attempt == 0;

        let mut request = HttpRequest::get(&artifact.url);
        if resuming {
            request = request.range_from(already);
            sink.emit(PrepareEvent::Resuming { path: artifact.path.clone(), from_bytes: already });
        }

        let response = http.send(request).await?;
        if !response.is_success() {
            return Err(AshError::UnexpectedStatus {
                url: artifact.url.clone(),
                status: response.status,
            });
        }

        // 206 means the server honoured the range and sent the tail. Anything
        // else - including a 200 from a server that ignored the header - is
        // the whole file, so start over rather than append to a prefix.
        let append = response.status == 206 && resuming;
        write_partial(&part_path, &response.body, append)?;

        let bytes = fs::read(&part_path)
            .map_err(|e| AshError::Storage { detail: format!("reading a partial file: {e}") })?;

        if bytes.len() as u64 == artifact.size && sha1_of(&bytes) == artifact.sha1 {
            return promote(&part_path, &final_path);
        }

        // Whatever is on disk is wrong. Drop it so the retry is a clean
        // fetch rather than a resume onto corrupt bytes.
        let _ = fs::remove_file(&part_path);
        if attempt == 0 {
            sink.emit(PrepareEvent::Reverifying { path: artifact.path.clone() });
        }
    }

    Err(AshError::VerificationFailed { path: artifact.path.clone() })
}

fn partial_path(depot_root: &Path, relative: &str) -> PathBuf {
    depot_root.join(format!("{relative}.part"))
}

fn write_partial(part_path: &Path, bytes: &[u8], append: bool) -> Result<(), AshError> {
    let parent = part_path.parent().expect("a depot path always has a parent");
    fs::create_dir_all(parent)
        .map_err(|e| AshError::Storage { detail: format!("creating {}: {e}", parent.display()) })?;

    let mut file = fs::OpenOptions::new()
        .create(true)
        .write(true)
        .append(append)
        .truncate(!append)
        .open(part_path)
        .map_err(|e| AshError::Storage { detail: format!("opening a partial file: {e}") })?;
    file.write_all(bytes)
        .map_err(|e| AshError::Storage { detail: format!("writing a partial file: {e}") })
}

/// Rename the verified partial into place. Only a file that has passed both
/// its size and hash check ever gets the real name.
fn promote(part_path: &Path, final_path: &Path) -> Result<(), AshError> {
    fs::rename(part_path, final_path)
        .map_err(|e| AshError::Storage { detail: format!("finishing a download: {e}") })
}

/// Fetch a metadata file the planner itself needs.
///
/// Verified exactly like any other artifact when a hash is published. The
/// version metadata and the asset index decide what everything else is, so
/// trusting them unchecked would undermine every hash below them.
pub(crate) async fn fetch_metadata(
    http: &dyn HttpPort,
    url: &str,
    expected_sha1: Option<&str>,
    expected_size: Option<u64>,
) -> Result<Vec<u8>, AshError> {
    let response: HttpResponse = http.send(HttpRequest::get(url)).await?;
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status });
    }

    if let Some(size) = expected_size {
        if response.body.len() as u64 != size {
            return Err(AshError::VerificationFailed { path: url.to_owned() });
        }
    }
    // Mojang has shipped manifest entries with an empty sha1 before, so an
    // absent hash is tolerated rather than treated as a failure.
    if let Some(sha1) = expected_sha1.filter(|s| !s.is_empty()) {
        if sha1_of(&response.body) != sha1 {
            return Err(AshError::VerificationFailed { path: url.to_owned() });
        }
    }
    Ok(response.body)
}

// ---- planning --------------------------------------------------------------

/// Work out every file a version target needs.
///
/// The player never lists anything: the version metadata names the client
/// jar and the libraries, and the asset index names the rest.
pub(crate) async fn plan(
    http: &dyn HttpPort,
    depot_root: &Path,
    source: &VersionSource,
    os: Os,
) -> Result<Plan, AshError> {
    let (version_id, version_url) = (source.id.as_str(), source.url.as_str());
    let metadata_bytes =
        fetch_metadata(http, version_url, Some(&source.sha1), None).await?;
    write_atomically(depot_root, &version_json_path(version_id), &metadata_bytes)?;
    let metadata = version::parse(version_url, &metadata_bytes)?;

    // The manifest and the metadata disagreeing about which version this is
    // means one of them is not what we asked for.
    if metadata.id != version_id {
        return Err(AshError::UnknownVersion { version_id: version_id.to_owned() });
    }

    let mut artifacts: Vec<Artifact> = Vec::new();

    if let Some(client) = &metadata.downloads.client {
        artifacts.push(Artifact {
            url: client.url.clone(),
            sha1: client.sha1.clone(),
            size: client.size,
            path: version_jar_path(version_id),
        });
    }

    // Selected here, not at launch: downloading macOS natives onto Windows
    // is waste the player pays for in bandwidth. Selection is by rule *and*
    // by native classifier, because a 1.21.x manifest gives all three
    // Windows native jars the same rule.
    for library in version::select_libraries(&metadata.libraries, os) {
        if let Some(artifact) = &library.downloads.artifact {
            if let Some(relative) = &artifact.path {
                artifacts.push(Artifact {
                    url: artifact.url.clone(),
                    sha1: artifact.sha1.clone(),
                    size: artifact.size,
                    path: library_path(relative),
                });
            }
        }
        if let Some(native) = library.natives_for(os) {
            if let Some(relative) = &native.path {
                artifacts.push(Artifact {
                    url: native.url.clone(),
                    sha1: native.sha1.clone(),
                    size: native.size,
                    path: library_path(relative),
                });
            }
        }
    }

    // Mojang publishes a log4j configuration per version, and for 1.7 to
    // 1.11 that file *is* the Log4Shell mitigation. It is an artifact like
    // any other, so it is planned, hashed and verified like any other.
    if let Some(logging) = metadata.logging.as_ref().and_then(|l| l.client.as_ref()) {
        artifacts.push(Artifact {
            url: logging.file.url.clone(),
            sha1: logging.file.sha1.clone(),
            size: logging.file.size,
            path: log_config_path(&logging.file.id),
        });
    }

    if let Some(index) = &metadata.asset_index {
        let index_bytes =
            fetch_metadata(http, &index.url, Some(&index.sha1), Some(index.size)).await?;
        write_atomically(depot_root, &asset_index_path(&index.id), &index_bytes)?;

        let parsed = version::parse_asset_index(&index.url, &index_bytes)?;
        for object in parsed.objects.values() {
            artifacts.push(Artifact {
                url: version::asset_url(&object.hash),
                sha1: object.hash.clone(),
                size: object.size,
                path: version::asset_path(&object.hash),
            });
        }
    }

    // Two instances on one version share every byte, so the same asset
    // appearing twice in a plan must not be downloaded twice.
    artifacts.sort_by(|a, b| a.path.cmp(&b.path));
    artifacts.dedup_by(|a, b| a.path == b.path);

    let total_files = artifacts.len();
    let missing: Vec<Artifact> =
        artifacts.into_iter().filter(|a| !is_present(depot_root, a)).collect();

    Ok(Plan {
        version_id: version_id.to_owned(),
        java_component: metadata.java_version.as_ref().map(|j| j.component.clone()),
        total_files,
        missing_files: missing.len(),
        missing_bytes: missing.iter().map(|a| a.size).sum(),
        missing,
    })
}

// ---- downloading -----------------------------------------------------------

/// Fetch a set of artifacts with bounded parallelism, verifying each.
///
/// Shared by version preparation and Java runtime provisioning: a runtime is
/// several hundred files with published hashes, which is the same problem.
pub(crate) async fn download_all<S: ProgressSink + ?Sized>(
    http: &dyn HttpPort,
    depot_root: &Path,
    artifacts: &[Artifact],
    sink: &S,
    cancel: &Cancel,
) -> Result<(), AshError> {
    if cancel.is_cancelled() {
        sink.emit(PrepareEvent::Cancelled);
        return Err(AshError::Cancelled);
    }

    let done_files = AtomicU64::new(0);
    let done_bytes = AtomicU64::new(0);

    // Owned clones, not references: an async block that borrows the closure
    // argument pins it to one lifetime, and `buffer_unordered` needs the
    // closure to be higher-ranked over any of them. An Artifact is three
    // strings, so this costs nothing worth the contortion of avoiding it.
    let results: Vec<Result<(), AshError>> = stream::iter(artifacts.iter().cloned())
        .map(|artifact| {
            let done_files = &done_files;
            let done_bytes = &done_bytes;
            async move {
                if cancel.is_cancelled() {
                    return Err(AshError::Cancelled);
                }
                fetch_verified(http, depot_root, &artifact, sink).await?;

                let files = done_files.fetch_add(1, Ordering::SeqCst) + 1;
                let bytes = done_bytes.fetch_add(artifact.size, Ordering::SeqCst) + artifact.size;
                sink.emit(PrepareEvent::Downloaded {
                    path: artifact.path,
                    bytes: artifact.size,
                    done_files: files as usize,
                    done_bytes: bytes,
                });
                Ok(())
            }
        })
        .buffer_unordered(CONCURRENCY)
        .collect()
        .await;

    // Report the first real failure. Cancellation is not a failure of the
    // download, so it is reported as itself.
    for result in results {
        match result {
            Ok(()) => {}
            Err(AshError::Cancelled) => {
                sink.emit(PrepareEvent::Cancelled);
                return Err(AshError::Cancelled);
            }
            Err(e) => return Err(e),
        }
    }
    Ok(())
}

/// Already in the depot and intact? Public within the crate so runtime
/// provisioning can skip work the same way preparation does.
pub(crate) fn present(depot_root: &Path, artifact: &Artifact) -> bool {
    is_present(depot_root, artifact)
}

/// Read a version's metadata back out of the depot.
///
/// `plan` writes it there after verifying it against Mojang's published
/// hash, so launching reads the same bytes the plan was built from rather
/// than asking the network again and hoping for the same answer.
pub(crate) fn read_metadata(
    depot_root: &Path,
    version_id: &str,
) -> Result<version::VersionMetadata, AshError> {
    let relative = version_json_path(version_id);
    let bytes = fs::read(depot_root.join(&relative))
        .map_err(|e| AshError::Storage { detail: format!("reading version metadata: {e}") })?;
    version::parse(&relative, &bytes)
}

// ---- preparing -------------------------------------------------------------

pub(crate) async fn prepare<S: ProgressSink + ?Sized>(
    http: &dyn HttpPort,
    depot_root: &Path,
    source: &VersionSource,
    os: Os,
    sink: &S,
    cancel: &Cancel,
) -> Result<Plan, AshError> {
    sink.emit(PrepareEvent::Resolving { version_id: source.id.clone() });

    let plan = plan(http, depot_root, source, os).await?;
    sink.emit(PrepareEvent::Planned {
        total_files: plan.total_files,
        missing_files: plan.missing_files,
        missing_bytes: plan.missing_bytes,
        already_present: plan.total_files - plan.missing_files,
    });

    if cancel.is_cancelled() {
        sink.emit(PrepareEvent::Cancelled);
        return Err(AshError::Cancelled);
    }

    download_all(http, depot_root, &plan.missing, sink, cancel).await?;

    // Unpacking is part of being prepared. A version whose natives are still
    // inside their jars has every file it needs and cannot start.
    let metadata = read_metadata(depot_root, &plan.version_id)?;
    natives::extract(depot_root, &metadata, os)?;

    Ok(plan)
}
