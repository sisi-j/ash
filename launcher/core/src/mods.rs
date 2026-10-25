//! Mods from Modrinth (spec 0005, ADR-0026, research 0014): searched for an
//! instance's version and loader, installed with what they require, each file
//! checked against Modrinth's SHA-512 before it goes into the instance's
//! `mods` folder.
//!
//! The folder stays the player's: ash puts a file there only when the player
//! installs that mod. What ash installed is recorded in the instance's
//! `ash-mods.json`, for listing and, later, updating.

use std::collections::{HashSet, VecDeque};
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha512};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest};
use crate::loader::Loader;

/// Fabric API on Modrinth. ash bundles the whole of it on modern targets, so
/// a dependency on it is already met there (research 0014 §5).
const FABRIC_API: &str = "P7dR8mSH";

/// Mods ash suggests, the Mods page's first view: well kept, for the client,
/// and none doing what ash already does. Modrinth decides which of them fit
/// the instance's version.
const SUGGESTED_FABRIC: &[&str] = &[
    "AANobbMI", // Sodium
    "gvQqBUqZ", // Lithium
    "uXXizFIs", // FerriteCore
    "NNAgCjsB", // Entity Culling
    "5ZwdcRci", // ImmediatelyFast
    "nmDcB62a", // ModernFix
    "LQ3K71Q1", // Dynamic FPS
    "mOgUt4GM", // Mod Menu
    "YL57xq9U", // Iris Shaders
    "1IjD5062", // Continuity
    "w7ThoJFB", // Zoomify
    "EsAfCjCV", // AppleSkin
    "aC3cM3Vq", // Mouse Tweaks
    "zV5r3pPn", // 3D Skin Layers
];

const SUGGESTED_LEGACY_FABRIC: &[&str] = &[
    "fLCmgUPa", // Phosphor-Legacy
    "eACKl8af", // MenuFPSUnlocker
    "7ZoK90Xo", // Windows Priority Boost
    "7vrSE44N", // Perspective Nametag
];

const MANIFEST: &str = "ash-mods.json";

/// Modrinth asks every client to name itself uniquely. The repository, not a
/// person: nobody's address goes to a third party.
const USER_AGENT: &str =
    concat!("sisi-j/ash/", env!("CARGO_PKG_VERSION"), " (+https://github.com/sisi-j/ash)");

/// Modrinth's name for an instance's loader, or none for vanilla.
pub(crate) fn loader_tag(loader: Loader) -> Option<&'static str> {
    match loader {
        Loader::Vanilla => None,
        Loader::Fabric => Some("fabric"),
        Loader::LegacyFabric => Some("legacy-fabric"),
    }
}

// ---- what the Mods page shows ----------------------------------------------

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct ModHit {
    pub project_id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    /// Already in this instance, from Modrinth.
    pub installed: bool,
    /// Comes with ash on this instance - Fabric API, on modern targets - so
    /// there is nothing to install.
    pub included: bool,
}

/// How a search orders what it finds: the Mods page's sort menu.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ModSort {
    /// ash's picks for the loader, most downloaded first.
    #[default]
    Suggested,
    /// Best match for what's typed; with nothing typed, most downloaded.
    Relevance,
    Downloads,
    Follows,
    Newest,
    Updated,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct ModSearch {
    pub hits: Vec<ModHit>,
    pub total: u64,
}

/// One mod ash installed, as `ash-mods.json` records it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct InstalledMod {
    pub project_id: String,
    pub title: String,
    pub version_id: String,
    pub version_number: String,
    /// `release`, `beta` or `alpha`.
    pub version_type: String,
    pub file_name: String,
    pub sha512: String,
    pub icon_url: Option<String>,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct Manifest {
    mods: Vec<InstalledMod>,
}

/// What installing a mod did.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct InstallOutcome {
    /// The mod and each dependency newly installed or replaced, in order.
    pub installed: Vec<InstalledMod>,
    /// Third-party mods were off for this instance, and installing turned them on.
    pub mods_turned_on: bool,
    /// Optional dependencies, by name, listed rather than installed.
    pub optional: Vec<String>,
}

// ---- Modrinth's answers (research 0014) -------------------------------------

#[derive(Deserialize)]
struct SearchBody {
    hits: Vec<SearchHit>,
    total_hits: u64,
}

#[derive(Deserialize)]
struct SearchHit {
    project_id: String,
    slug: String,
    title: String,
    #[serde(default)]
    description: String,
    icon_url: Option<String>,
    #[serde(default)]
    downloads: u64,
}

#[derive(Deserialize, Clone)]
struct Version {
    id: String,
    project_id: String,
    version_number: String,
    version_type: String,
    files: Vec<VersionFile>,
    #[serde(default)]
    dependencies: Vec<Dependency>,
}

#[derive(Deserialize, Clone)]
struct VersionFile {
    url: String,
    filename: String,
    #[serde(default)]
    primary: bool,
    hashes: Hashes,
}

#[derive(Deserialize, Clone)]
struct Hashes {
    sha512: String,
}

#[derive(Deserialize, Clone)]
struct Dependency {
    version_id: Option<String>,
    project_id: Option<String>,
    dependency_type: String,
}

#[derive(Deserialize)]
struct Project {
    id: String,
    title: String,
    icon_url: Option<String>,
}

// ---- asking -----------------------------------------------------------------

/// Query-string encoding: what isn't unreserved is %-escaped.
fn encode(value: &str) -> String {
    value
        .bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

async fn get_json<T: serde::de::DeserializeOwned>(
    http: &dyn HttpPort,
    url: &str,
) -> Result<T, AshError> {
    let mut request = HttpRequest::get(url);
    request.headers.push(("User-Agent".into(), USER_AGENT.into()));
    let response = http.send(request).await?;
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status });
    }
    serde_json::from_slice(&response.body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}

/// What the player asked the Mods page for: the typed text, the sort, and
/// how far down the list.
pub(crate) struct Wanted<'a> {
    pub query: &'a str,
    pub sort: ModSort,
    pub offset: u32,
}

pub(crate) async fn search(
    http: &dyn HttpPort,
    base: &str,
    tag: &str,
    game_version: &str,
    wanted: Wanted<'_>,
    installed: &[InstalledMod],
) -> Result<ModSearch, AshError> {
    let Wanted { query, sort, offset } = wanted;
    let mut facets =
        format!(r#"[["project_type:mod"],["versions:{game_version}"],["categories:{tag}"]"#);
    if sort == ModSort::Suggested {
        let picks = if tag == "fabric" { SUGGESTED_FABRIC } else { SUGGESTED_LEGACY_FABRIC };
        let any_of: Vec<String> = picks.iter().map(|id| format!(r#""project_id:{id}""#)).collect();
        facets.push_str(&format!(",[{}]", any_of.join(",")));
    }
    facets.push(']');
    let index = match sort {
        ModSort::Relevance if !query.trim().is_empty() => "relevance",
        // Relevance to nothing typed means nothing: most downloaded instead.
        ModSort::Suggested | ModSort::Relevance | ModSort::Downloads => "downloads",
        ModSort::Follows => "follows",
        ModSort::Newest => "newest",
        ModSort::Updated => "updated",
    };
    let url = format!(
        "{base}/search?query={}&facets={}&index={index}&limit=20&offset={offset}",
        encode(query.trim()),
        encode(&facets)
    );
    let body: SearchBody = get_json(http, &url).await?;
    Ok(ModSearch {
        total: body.total_hits,
        hits: body
            .hits
            .into_iter()
            .map(|h| ModHit {
                installed: installed.iter().any(|m| m.project_id == h.project_id),
                included: tag == "fabric" && h.project_id == FABRIC_API,
                project_id: h.project_id,
                slug: h.slug,
                title: h.title,
                description: h.description,
                icon_url: h.icon_url,
                downloads: h.downloads,
            })
            .collect(),
    })
}

async fn versions(
    http: &dyn HttpPort,
    base: &str,
    project: &str,
    tag: &str,
    game_version: &str,
) -> Result<Vec<Version>, AshError> {
    let url = format!(
        "{base}/project/{}/version?loaders={}&game_versions={}",
        encode(project),
        encode(&format!(r#"["{tag}"]"#)),
        encode(&format!(r#"["{game_version}"]"#))
    );
    get_json(http, &url).await
}

/// The newest release; failing that, the newest of any type (research 0014 §3).
/// Modrinth lists them newest first.
fn choose(versions: Vec<Version>) -> Option<Version> {
    let release = versions.iter().position(|v| v.version_type == "release");
    match release {
        Some(i) => versions.into_iter().nth(i),
        None => versions.into_iter().next(),
    }
}

async fn projects(
    http: &dyn HttpPort,
    base: &str,
    ids: &[String],
) -> Result<Vec<Project>, AshError> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let list =
        format!("[{}]", ids.iter().map(|id| format!("\"{id}\"")).collect::<Vec<_>>().join(","));
    get_json(http, &format!("{base}/projects?ids={}", encode(&list))).await
}

// ---- the record --------------------------------------------------------------

pub(crate) fn manifest_path(instance_dir: &Path) -> PathBuf {
    instance_dir.join(MANIFEST)
}

pub(crate) fn installed(instance_dir: &Path) -> Vec<InstalledMod> {
    std::fs::read(manifest_path(instance_dir))
        .ok()
        .and_then(|bytes| serde_json::from_slice::<Manifest>(&bytes).ok())
        .map(|m| m.mods)
        .unwrap_or_default()
}

pub(crate) fn save(instance_dir: &Path, mods: Vec<InstalledMod>) -> Result<(), AshError> {
    let encoded = serde_json::to_vec_pretty(&Manifest { mods })
        .map_err(|e| AshError::Storage { detail: format!("encoding ash-mods.json: {e}") })?;
    std::fs::write(manifest_path(instance_dir), encoded)
        .map_err(AshError::writing("writing ash-mods.json"))
}

// ---- installing ----------------------------------------------------------------

/// Everything installing `root` takes: the versions to fetch, in order, and
/// the optional dependencies to mention. Nothing is written.
struct Plan {
    versions: Vec<Version>,
    optional: Vec<String>,
}

/// What a mod is being installed into.
pub(crate) struct Target<'a> {
    pub loader: Loader,
    pub game_version: &'a str,
}

/// A project's name, for an error about it; its id if even that can't be had.
async fn title(http: &dyn HttpPort, base: &str, project: &str) -> String {
    get_json::<Project>(http, &format!("{base}/project/{}", encode(project)))
        .await
        .map(|p| p.title)
        .unwrap_or_else(|_| project.to_owned())
}

async fn plan(
    http: &dyn HttpPort,
    base: &str,
    target: &Target<'_>,
    tag: &str,
    root: &Project,
    have: &[InstalledMod],
) -> Result<Plan, AshError> {
    let (loader, game_version) = (target.loader, target.game_version);
    let named = |project: &str| if project == root.id { Some(root.title.clone()) } else { None };
    let mut queue: VecDeque<(String, Option<String>)> = VecDeque::from([(root.id.clone(), None)]);
    let mut seen = HashSet::new();
    let mut planned: Vec<Version> = Vec::new();
    let mut optional = Vec::new();
    while let Some((project, pinned)) = queue.pop_front() {
        if loader == Loader::Fabric && project == FABRIC_API {
            continue; // ash's bundle has it (research 0014 §5)
        }
        if !seen.insert(project.clone()) {
            continue;
        }
        let existing = have.iter().find(|m| m.project_id == project);
        let version = match &pinned {
            Some(id) => {
                get_json::<Version>(http, &format!("{base}/version/{}", encode(id))).await?
            }
            None => match choose(versions(http, base, &project, tag, game_version).await?) {
                Some(version) => version,
                None => {
                    let title = match named(&project) {
                        Some(title) => title,
                        None => title(http, base, &project).await,
                    };
                    return Err(AshError::ModUnavailable { title });
                }
            },
        };
        // A dependency already here stays, unless it pins another version.
        let keep = project != root.id
            && existing.is_some_and(|m| pinned.is_none() || m.version_id == version.id);
        if keep {
            continue;
        }
        for dep in &version.dependencies {
            let Some(dep_project) = dep.project_id.clone() else { continue };
            match dep.dependency_type.as_str() {
                "required" => queue.push_back((dep_project, dep.version_id.clone())),
                "optional" => optional.push(dep_project),
                "incompatible" => {
                    if let Some(clash) = have.iter().find(|m| m.project_id == dep_project) {
                        let title = match named(&version.project_id) {
                            Some(title) => title,
                            None => title(http, base, &version.project_id).await,
                        };
                        return Err(AshError::ModIncompatible { title, with: clash.title.clone() });
                    }
                }
                _ => {} // embedded: inside the jar already
            }
        }
        planned.push(version);
    }
    Ok(Plan { versions: planned, optional })
}

fn sha512_hex(bytes: &[u8]) -> String {
    Sha512::digest(bytes).iter().map(|b| format!("{b:02x}")).collect()
}

/// Install `root` into the instance: the mod and what it requires, each file
/// checked before it's moved in, then recorded.
pub(crate) async fn install(
    http: &dyn HttpPort,
    base: &str,
    target: &Target<'_>,
    instance_dir: &Path,
    mods_dir: &Path,
    root: &str,
) -> Result<(Vec<InstalledMod>, Vec<String>), AshError> {
    let tag = loader_tag(target.loader).ok_or(AshError::ModsNeedFabric)?;
    let have = installed(instance_dir);
    let root_project: Project = get_json(http, &format!("{base}/project/{}", encode(root))).await?;
    let plan = plan(http, base, target, tag, &root_project, &have).await?;

    let mut ids: Vec<String> = plan.versions.iter().map(|v| v.project_id.clone()).collect();
    ids.extend(plan.optional.iter().cloned());
    let known = projects(http, base, &ids).await?;
    // The mod asked for is known already; the rest come from the batch.
    let known: Vec<&Project> = std::iter::once(&root_project).chain(known.iter()).collect();
    let title_of = |id: &str| {
        known.iter().find(|p| p.id == id).map(|p| p.title.clone()).unwrap_or_else(|| id.to_owned())
    };
    let icon_of = |id: &str| known.iter().find(|p| p.id == id).and_then(|p| p.icon_url.clone());

    std::fs::create_dir_all(mods_dir).map_err(AshError::writing("creating the mods folder"))?;
    let mut record = have.clone();
    let mut done = Vec::new();
    for version in &plan.versions {
        let file = version
            .files
            .iter()
            .find(|f| f.primary)
            .or_else(|| version.files.first())
            .ok_or_else(|| AshError::ModUnavailable { title: title_of(&version.project_id) })?;
        let mut request = HttpRequest::get(&file.url);
        request.headers.push(("User-Agent".into(), USER_AGENT.into()));
        let response = http.send(request).await?;
        if !response.is_success() {
            return Err(AshError::UnexpectedStatus {
                url: file.url.clone(),
                status: response.status,
            });
        }
        if sha512_hex(&response.body) != file.hashes.sha512.to_ascii_lowercase() {
            return Err(AshError::VerificationFailed { path: file.filename.clone() });
        }
        // Written beside, then moved in: a half-written jar never sits in the folder.
        let part = mods_dir.join(format!("{}.part", file.filename));
        std::fs::write(&part, &response.body).map_err(AshError::writing("writing a mod"))?;
        std::fs::rename(&part, mods_dir.join(&file.filename))
            .map_err(AshError::writing("moving a mod into place"))?;
        // The version it replaces, if any, goes.
        if let Some(old) = record.iter().find(|m| m.project_id == version.project_id) {
            if old.file_name != file.filename {
                let _ = std::fs::remove_file(mods_dir.join(&old.file_name));
                let _ = std::fs::remove_file(mods_dir.join(format!("{}.disabled", old.file_name)));
            }
        }
        record.retain(|m| m.project_id != version.project_id);
        let entry = InstalledMod {
            project_id: version.project_id.clone(),
            title: title_of(&version.project_id),
            version_id: version.id.clone(),
            version_number: version.version_number.clone(),
            version_type: version.version_type.clone(),
            file_name: file.filename.clone(),
            sha512: file.hashes.sha512.to_ascii_lowercase(),
            icon_url: icon_of(&version.project_id),
        };
        record.push(entry.clone());
        done.push(entry);
        save(instance_dir, record.clone())?;
    }
    let optional = plan.optional.iter().map(|id| title_of(id)).collect();
    Ok((done, optional))
}

// ---- what's in the folder (#146) ---------------------------------------------

/// Mods on Modrinth that do what an ash feature does, by project id, and that
/// feature's name (ADR-0026). Noted, never blocked: ash's features step aside
/// when a mod clashes with their hooks (ADR-0017). Only clear overlaps of
/// widely used mods; a tab-list ping isn't ash's on-screen ping readout.
const OVERLAPS: &[(&str, &str)] = &[
    ("g4pR0Fmy", "Freelook"),      // Freelook
    ("DSA6X7rF", "Freelook"),      // FreeLook (the original)
    ("S1v2ASLQ", "Freelook"),      // Freelook (Perspective Mod)
    ("N8Pmw3EX", "Toggle sprint"), // Toggle Sprint
    ("gQ6IIk5e", "Toggle sprint"), // Toggle Sprint
    ("i9xRThb3", "Toggle sprint"), // PolySprint
    ("DIlqwRFH", "FPS readout"),   // FPS - Display
    ("hKRDgptX", "FPS readout"),   // FPS Display+
    ("o1tyE5vJ", "Crosshair"),     // Custom Crosshair Mod
    ("obfJASPU", "Crosshair"),     // PolyCrosshair
    ("6zatK8W5", "Hit colour"),    // DamageTint
    ("VXFg68YU", "Hit colour"),    // HitColor
    ("vJofZXEN", "Hit colour"),    // Hit Color
    ("4Hi9HZKQ", "Snaplook"),      // Snaplook
    ("sAFPANmp", "Snaplook"),      // BehindYou
];

pub(crate) fn overlap(project_id: &str) -> Option<&'static str> {
    OVERLAPS.iter().find(|(id, _)| *id == project_id).map(|(_, feature)| *feature)
}

const DISABLED: &str = ".disabled";

/// One jar in the instance's `mods` folder, however it got there.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct ModEntry {
    /// Its name on disk, without `.disabled`.
    pub file_name: String,
    pub enabled: bool,
    pub title: String,
    pub version: Option<String>,
    /// Installed by ash from Modrinth, rather than added by hand.
    pub from_modrinth: bool,
    pub icon_url: Option<String>,
    /// The ash feature it does the same job as, if any.
    pub overlaps: Option<String>,
}

/// A jar's own name and version, from its `fabric.mod.json`.
fn jar_metadata(path: &Path) -> Option<(String, Option<String>)> {
    use std::io::Read;
    let mut archive = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let mut text = String::new();
    archive.by_name("fabric.mod.json").ok()?.read_to_string(&mut text).ok()?;
    let meta: serde_json::Value = serde_json::from_str(&text).ok()?;
    let name = meta["name"].as_str().or_else(|| meta["id"].as_str())?.to_owned();
    Some((name, meta["version"].as_str().map(str::to_owned)))
}

/// Every jar in the folder, on or off, by name.
pub(crate) fn list(instance_dir: &Path, mods_dir: &Path) -> Vec<ModEntry> {
    let record = installed(instance_dir);
    let Ok(entries) = std::fs::read_dir(mods_dir) else { return Vec::new() };
    let mut mods: Vec<ModEntry> = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().to_string_lossy().into_owned();
            let (file_name, enabled) = match name.strip_suffix(DISABLED) {
                Some(base) => (base.to_owned(), false),
                None => (name.clone(), true),
            };
            if !file_name.ends_with(".jar") {
                return None;
            }
            Some(match record.iter().find(|m| m.file_name == file_name) {
                Some(m) => ModEntry {
                    file_name,
                    enabled,
                    title: m.title.clone(),
                    version: Some(m.version_number.clone()),
                    from_modrinth: true,
                    icon_url: m.icon_url.clone(),
                    overlaps: overlap(&m.project_id).map(str::to_owned),
                },
                None => {
                    let (title, version) = jar_metadata(&entry.path())
                        .unwrap_or_else(|| (file_name.trim_end_matches(".jar").to_owned(), None));
                    ModEntry {
                        file_name,
                        enabled,
                        title,
                        version,
                        from_modrinth: false,
                        icon_url: None,
                        overlaps: None,
                    }
                }
            })
        })
        .collect();
    mods.sort_by_key(|m| m.title.to_lowercase());
    mods
}

/// A jar's name as the Mods page sent it: a plain `.jar` file name, nothing
/// that could reach outside the folder.
fn plain_jar(file_name: &str) -> Result<&str, AshError> {
    let plain = file_name.ends_with(".jar")
        && !file_name.contains(['/', '\\', ':'])
        && file_name != ".jar"
        && !file_name.starts_with('.');
    if plain {
        Ok(file_name)
    } else {
        Err(AshError::FileVanished { path: file_name.to_owned() })
    }
}

/// Turn a mod on or off: the `.jar.disabled` rename other launchers use too.
pub(crate) fn set_enabled(mods_dir: &Path, file_name: &str, enabled: bool) -> Result<(), AshError> {
    let file_name = plain_jar(file_name)?;
    let on = mods_dir.join(file_name);
    let off = mods_dir.join(format!("{file_name}{DISABLED}"));
    let (from, to) = if enabled { (off, on) } else { (on, off) };
    if !from.exists() {
        return if to.exists() {
            Ok(())
        } else {
            Err(AshError::FileVanished { path: file_name.to_owned() })
        };
    }
    std::fs::rename(from, to).map_err(AshError::writing("switching a mod"))
}

/// Remove a mod's file, on or off, and ash's record of it.
pub(crate) fn remove(
    instance_dir: &Path,
    mods_dir: &Path,
    file_name: &str,
) -> Result<(), AshError> {
    let file_name = plain_jar(file_name)?;
    for path in [mods_dir.join(file_name), mods_dir.join(format!("{file_name}{DISABLED}"))] {
        if path.exists() {
            std::fs::remove_file(path).map_err(AshError::writing("removing a mod"))?;
        }
    }
    let mut record = installed(instance_dir);
    let before = record.len();
    record.retain(|m| m.file_name != file_name);
    if record.len() != before {
        save(instance_dir, record)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn query_values_are_escaped_and_plain_ones_left_alone() {
        assert_eq!(encode("sodium"), "sodium");
        assert_eq!(encode(r#"[["versions:1.21.11"]]"#), "%5B%5B%22versions%3A1.21.11%22%5D%5D");
        assert_eq!(encode("mod menu"), "mod%20menu");
    }
}
