//! Java runtime provisioning.
//!
//! ash downloads and manages its own JREs from Mojang's runtime manifest and
//! never looks at system Java. A player should not have to know that 1.8.9
//! wants Java 8 and 1.21.x wants Java 21, and whatever happens to be on PATH
//! is almost certainly neither.
//!
//! Nothing here reads `PATH` or `JAVA_HOME`, and nothing here ever will. A
//! player naming a specific binary as a machine-local override is a
//! different thing - that is a decision they made, not a guess ash made -
//! and it is applied in `launch.rs`, never by searching from in here.

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::depot::{self, Artifact, Cancel, PrepareEvent, ProgressSink};
use crate::error::AshError;
use crate::http::HttpPort;
use crate::version::Os;

/// Mojang's index of every runtime for every platform.
pub const RUNTIME_MANIFEST_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

/// What Mojang calls a version target's runtime when the metadata says
/// nothing. Versions from the 1.8 era predate the `javaVersion` field.
const DEFAULT_COMPONENT: &str = "jre-legacy";

/// A provisioned runtime, ready to launch with.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Runtime {
    /// Mojang's component name, e.g. `jre-legacy` or `java-runtime-delta`.
    pub component: String,
    pub version_name: String,
    /// Absolute path to the java executable, always inside the depot.
    pub java_executable: PathBuf,
}

// ---- wire format -----------------------------------------------------------

/// platform -> component -> candidates
type RuntimeIndex = HashMap<String, HashMap<String, Vec<RuntimeCandidate>>>;

#[derive(Debug, Clone, Deserialize)]
struct RuntimeCandidate {
    manifest: ManifestRef,
    version: RuntimeVersion,
}

#[derive(Debug, Clone, Deserialize)]
struct ManifestRef {
    sha1: String,
    size: u64,
    url: String,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeVersion {
    name: String,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeFiles {
    files: HashMap<String, RuntimeFile>,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeFile {
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    downloads: Option<RuntimeFileDownloads>,
    #[serde(default)]
    executable: bool,
    /// Only present on `type: "link"` entries.
    #[serde(default)]
    target: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
struct RuntimeFileDownloads {
    /// ash takes the raw file. Mojang also publishes an LZMA-compressed
    /// variant, but decompressing it would mean a compression dependency to
    /// save bandwidth on a one-off download.
    raw: RawDownload,
}

#[derive(Debug, Clone, Deserialize)]
struct RawDownload {
    sha1: String,
    size: u64,
    url: String,
}

// ---- platform --------------------------------------------------------------

/// Mojang's key for the platform ash is running on.
///
/// Architecture comes from the build target rather than a parameter: a depot
/// belongs to one machine, so there is only ever one right answer here.
pub fn platform_key(os: Os) -> &'static str {
    match os {
        Os::Windows => {
            if cfg!(target_arch = "aarch64") {
                "windows-arm64"
            } else {
                "windows-x64"
            }
        }
        Os::MacOs => {
            if cfg!(target_arch = "aarch64") {
                "mac-os-arm64"
            } else {
                "mac-os"
            }
        }
    }
}

fn runtime_root(platform: &str, component: &str) -> String {
    format!("runtimes/{platform}/{component}")
}

/// The component a version target asks for, falling back for old versions
/// whose metadata predates the field.
pub fn component_for(java_component: Option<&str>) -> String {
    java_component.unwrap_or(DEFAULT_COMPONENT).to_owned()
}

// ---- provisioning ----------------------------------------------------------

/// Download and lay out the runtime a version target needs.
///
/// Idempotent: files already in the depot are skipped, so a second instance
/// on the same component costs nothing.
pub(crate) async fn provision<S: ProgressSink + ?Sized>(
    http: &dyn HttpPort,
    depot_root: &Path,
    component: &str,
    os: Os,
    sink: &S,
    cancel: &Cancel,
) -> Result<Runtime, AshError> {
    let platform = platform_key(os);

    let index_bytes = depot::fetch_metadata(http, RUNTIME_MANIFEST_URL, None, None).await?;
    let index: RuntimeIndex = serde_json::from_slice(&index_bytes).map_err(|e| {
        AshError::Malformed { url: RUNTIME_MANIFEST_URL.to_owned(), detail: e.to_string() }
    })?;

    let candidate = index
        .get(platform)
        .and_then(|components| components.get(component))
        .and_then(|candidates| candidates.first())
        .ok_or_else(|| AshError::RuntimeUnavailable {
            component: component.to_owned(),
            platform: platform.to_owned(),
        })?;

    // The per-runtime manifest is verified like any other metadata: it
    // decides what every file below it is.
    let files_bytes = depot::fetch_metadata(
        http,
        &candidate.manifest.url,
        Some(&candidate.manifest.sha1),
        Some(candidate.manifest.size),
    )
    .await?;
    let listing: RuntimeFiles = serde_json::from_slice(&files_bytes).map_err(|e| {
        AshError::Malformed { url: candidate.manifest.url.clone(), detail: e.to_string() }
    })?;

    let root = runtime_root(platform, component);
    let mut artifacts = Vec::new();
    let mut executables = Vec::new();
    let mut links = Vec::new();

    for (relative, entry) in &listing.files {
        let path = format!("{root}/{relative}");
        match entry.kind.as_str() {
            "file" => {
                let Some(downloads) = &entry.downloads else {
                    continue;
                };
                artifacts.push(Artifact {
                    url: downloads.raw.url.clone(),
                    sha1: downloads.raw.sha1.clone(),
                    size: downloads.raw.size,
                    path: path.clone(),
                });
                if entry.executable {
                    executables.push(path);
                }
            }
            "directory" => {
                fs::create_dir_all(depot_root.join(&path))
                    .map_err(AshError::writing("creating a runtime directory"))?;
            }
            "link" => {
                if let Some(target) = &entry.target {
                    links.push((path, target.clone()));
                }
            }
            _ => {}
        }
    }

    // Deterministic order so a failure is reproducible rather than depending
    // on hash-map iteration.
    artifacts.sort_by(|a, b| a.path.cmp(&b.path));

    let missing: Vec<Artifact> =
        artifacts.iter().filter(|a| !depot::present(depot_root, a)).cloned().collect();

    sink.emit(PrepareEvent::Runtime { component: component.to_owned() });
    sink.emit(PrepareEvent::Planned {
        total_files: artifacts.len(),
        missing_files: missing.len(),
        missing_bytes: missing.iter().map(|a| a.size).sum(),
        already_present: artifacts.len() - missing.len(),
    });

    depot::download_all(http, depot_root, &missing, sink, cancel).await?;

    for (path, target) in links {
        create_link(&depot_root.join(&path), &target)?;
    }
    for path in executables {
        mark_executable(&depot_root.join(&path))?;
    }

    let java_executable = find_java(depot_root, &root, &listing)?;

    Ok(Runtime {
        component: component.to_owned(),
        version_name: candidate.version.name.clone(),
        java_executable,
    })
}

/// Locate the java binary within a provisioned runtime.
///
/// Not hardcoded: Windows runtimes put it at `bin/java.exe`, while macOS
/// runtimes bury it under `jre.bundle/Contents/Home/bin/java`.
fn find_java(depot_root: &Path, root: &str, listing: &RuntimeFiles) -> Result<PathBuf, AshError> {
    let mut candidates: Vec<&String> = listing
        .files
        .keys()
        .filter(|relative| relative.ends_with("bin/java.exe") || relative.ends_with("bin/java"))
        .collect();
    // Shortest path wins, so a nested duplicate never shadows the real one.
    candidates.sort_by_key(|relative| relative.len());

    candidates.first().map(|relative| depot_root.join(format!("{root}/{relative}"))).ok_or_else(
        || AshError::Malformed {
            url: RUNTIME_MANIFEST_URL.to_owned(),
            detail: "the runtime manifest lists no java executable".into(),
        },
    )
}

#[cfg(unix)]
fn mark_executable(path: &Path) -> Result<(), AshError> {
    use std::os::unix::fs::PermissionsExt;
    let Ok(metadata) = fs::metadata(path) else {
        return Ok(());
    };
    let mut permissions = metadata.permissions();
    permissions.set_mode(permissions.mode() | 0o755);
    fs::set_permissions(path, permissions)
        .map_err(AshError::writing("marking a runtime file executable"))
}

#[cfg(not(unix))]
fn mark_executable(_path: &Path) -> Result<(), AshError> {
    // Windows has no executable bit.
    Ok(())
}

#[cfg(unix)]
fn create_link(path: &Path, target: &str) -> Result<(), AshError> {
    if path.exists() {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    std::os::unix::fs::symlink(target, path).map_err(AshError::writing("creating a runtime link"))
}

#[cfg(not(unix))]
fn create_link(_path: &Path, _target: &str) -> Result<(), AshError> {
    // Windows runtimes contain no link entries, and creating symlinks there
    // needs elevation ash should not be asking for.
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_version_without_a_java_component_falls_back_to_the_legacy_runtime() {
        // 1.8-era metadata predates the field; defaulting to a modern runtime
        // would hand Java 21 to a game that cannot run on it.
        assert_eq!(component_for(None), "jre-legacy");
        assert_eq!(component_for(Some("java-runtime-delta")), "java-runtime-delta");
    }

    #[test]
    fn the_platform_key_is_one_mojang_publishes() {
        for os in [Os::Windows, Os::MacOs] {
            let key = platform_key(os);
            assert!(
                ["windows-x64", "windows-arm64", "mac-os", "mac-os-arm64"].contains(&key),
                "unexpected platform key {key}"
            );
        }
    }
}
