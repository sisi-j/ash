use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::loader::Loader;

const METADATA_FILE: &str = "instance.json";
const GAME_DIR: &str = "minecraft";

/// Directories the game expects to own. Created up front so a player can drop
/// a resource pack in before ever launching.
const GAME_SUBDIRS: [&str; 5] = ["saves", "config", "resourcepacks", "screenshots", "mods"];

/// A filesystem-safe, stable identifier for an instance.
///
/// Derived from the name at creation and then immutable: renaming must not
/// move directories, because paths end up in JVM arguments, logs and the
/// player's own shortcuts.
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
pub struct InstanceId(String);

impl InstanceId {
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl std::fmt::Display for InstanceId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Instance {
    pub id: InstanceId,
    /// What the player called it. Free text, including non-ASCII.
    pub name: String,
    /// The version target this instance runs.
    pub version_id: String,
    /// The loader this instance runs under. Chosen at creation alongside the
    /// version target, and never changed afterwards - no operation on `Ash`
    /// takes a loader except creation.
    ///
    /// Defaulted on read so that every instance written before ash had
    /// loaders reads back as vanilla, which is what it is. Removing the
    /// default would make each of those a corrupt instance on the next
    /// launcher start, with the player's worlds inside it.
    #[serde(default = "vanilla")]
    pub loader: Loader,
    pub created_at_ms: u64,
    pub last_played_ms: Option<u64>,
    /// Every finished session's length, added together. A session still
    /// open in [`Instance::last_session`] is not in it yet.
    ///
    /// Defaulted on read, like `loader`: an instance from before ash kept
    /// play time has played for no time ash knows of, which is not corrupt.
    #[serde(default)]
    pub played_ms: u64,
    /// The latest session, open until it is seen to end.
    #[serde(default)]
    pub last_session: Option<Session>,
    /// Which instance this is on every computer the player syncs (research
    /// 0011). The id above is this machine's directory name, a slug of the
    /// name, and two machines' "PvP" instances share one by accident; this
    /// never does. `None` for an instance that isn't synced.
    #[serde(default)]
    pub sync_id: Option<String>,
    /// Deleted on another computer and kept here: this machine's alone from
    /// then on, so sync never gives it a sync id again.
    #[serde(default)]
    pub local_only: bool,
}

/// One run of the game, from ash starting it to its exit.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Session {
    pub started_ms: u64,
    /// `None` while the game runs - or after it stopped without ash seeing
    /// it, which [`unseen_end`] settles.
    pub ended_ms: Option<u64>,
}

/// What deleting an instance would destroy.
///
/// Worlds are listed by name rather than counted: "3 worlds" is not enough
/// information to decide with, and this is the one irreversible action in the
/// launcher.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct DeletionPreview {
    pub instance: Instance,
    pub worlds: Vec<String>,
    pub resource_packs: usize,
    pub screenshots: usize,
    pub total_bytes: u64,
}

/// What an `instance.json` written before ash had loaders describes.
///
/// A named function rather than a `Default` impl on [`Loader`]: this is the
/// only place in ash where a loader is not stated outright, and it is one
/// because the file predates the field. Everywhere else has to name one.
fn vanilla() -> Loader {
    Loader::Vanilla
}

// ---- paths ----------------------------------------------------------------

fn instance_dir(instances_root: &Path, id: &InstanceId) -> PathBuf {
    instances_root.join(id.as_str())
}

pub(crate) fn game_dir(instances_root: &Path, id: &InstanceId) -> PathBuf {
    instance_dir(instances_root, id).join(GAME_DIR)
}

fn metadata_path(instances_root: &Path, id: &InstanceId) -> PathBuf {
    instance_dir(instances_root, id).join(METADATA_FILE)
}

// ---- naming ---------------------------------------------------------------

/// Reduce a display name to something safe on every filesystem we target.
///
/// A name made entirely of characters this drops yields an empty slug, which
/// is why the caller falls back to a generic stem rather than rejecting the
/// name. The display name keeps the original either way.
fn slugify(name: &str) -> String {
    let mut out = String::new();
    let mut pending_dash = false;
    for c in name.chars() {
        if c.is_ascii_alphanumeric() {
            if pending_dash && !out.is_empty() {
                out.push('-');
            }
            out.push(c.to_ascii_lowercase());
            pending_dash = false;
        } else {
            pending_dash = true;
        }
    }
    out
}

fn unique_id(instances_root: &Path, name: &str) -> InstanceId {
    let stem = {
        let slug = slugify(name);
        if slug.is_empty() {
            "instance".to_owned()
        } else {
            slug
        }
    };

    if !instance_dir(instances_root, &InstanceId(stem.clone())).exists() {
        return InstanceId(stem);
    }
    for n in 2u32.. {
        let candidate = format!("{stem}-{n}");
        if !instance_dir(instances_root, &InstanceId(candidate.clone())).exists() {
            return InstanceId(candidate);
        }
    }
    unreachable!("the u32 range is not exhaustible in practice")
}

// ---- storage --------------------------------------------------------------

/// Milliseconds, not seconds. Two instances played inside the same second
/// would otherwise sort arbitrarily against each other.
fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or_default()
}

fn storage_err(context: &'static str) -> impl Fn(std::io::Error) -> AshError {
    move |e| AshError::Storage { detail: format!("{context}: {e}") }
}

fn write_metadata(instances_root: &Path, instance: &Instance) -> Result<(), AshError> {
    let path = metadata_path(instances_root, &instance.id);
    let encoded = serde_json::to_vec_pretty(instance)
        .map_err(|e| AshError::Storage { detail: format!("encoding instance metadata: {e}") })?;
    fs::write(&path, encoded).map_err(storage_err("writing instance metadata"))
}

fn read_metadata(instances_root: &Path, id: &InstanceId) -> Result<Instance, AshError> {
    let path = metadata_path(instances_root, id);
    let raw = fs::read(&path).map_err(|_| AshError::InstanceNotFound { id: id.to_string() })?;
    serde_json::from_slice(&raw)
        .map_err(|e| AshError::Storage { detail: format!("reading instance metadata: {e}") })
}

// ---- behaviour ------------------------------------------------------------

pub(crate) fn create(
    instances_root: &Path,
    name: &str,
    version_id: &str,
    loader: Loader,
) -> Result<Instance, AshError> {
    create_with(
        instances_root,
        name,
        version_id,
        loader,
        uuid::Uuid::new_v4().to_string(),
        now_ms(),
    )
}

/// An instance another computer made, arriving here by sync: its sync id and
/// creation time are that instance's, and its directory id is this machine's.
pub(crate) fn create_with(
    instances_root: &Path,
    name: &str,
    version_id: &str,
    loader: Loader,
    sync_id: String,
    created_at_ms: u64,
) -> Result<Instance, AshError> {
    let name = name.trim();
    if name.is_empty() {
        return Err(AshError::InvalidInstanceName { detail: "the name is empty".into() });
    }

    fs::create_dir_all(instances_root).map_err(storage_err("creating the instances directory"))?;

    let id = unique_id(instances_root, name);
    let game = game_dir(instances_root, &id);
    for sub in GAME_SUBDIRS {
        fs::create_dir_all(game.join(sub))
            .map_err(storage_err("creating the instance game directory"))?;
    }

    let instance = Instance {
        id,
        name: name.to_owned(),
        version_id: version_id.to_owned(),
        loader,
        created_at_ms,
        last_played_ms: None,
        played_ms: 0,
        last_session: None,
        sync_id: Some(sync_id),
        local_only: false,
    };
    write_metadata(instances_root, &instance)?;
    Ok(instance)
}

pub(crate) fn list(instances_root: &Path) -> Result<Vec<Instance>, AshError> {
    let entries = match fs::read_dir(instances_root) {
        Ok(entries) => entries,
        // No instances directory yet simply means no instances.
        Err(_) => return Ok(Vec::new()),
    };

    let mut instances: Vec<Instance> = entries
        .flatten()
        .filter(|e| e.path().is_dir())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter_map(|name| read_metadata(instances_root, &InstanceId(name)).ok())
        .collect();

    // Most recently played first, then never-played by creation date. This is
    // the order the launcher shows, so it belongs here rather than in the UI.
    instances.sort_by(|a, b| {
        b.last_played_ms.cmp(&a.last_played_ms).then_with(|| b.created_at_ms.cmp(&a.created_at_ms))
    });
    Ok(instances)
}

pub(crate) fn get(instances_root: &Path, id: &InstanceId) -> Result<Instance, AshError> {
    read_metadata(instances_root, id)
}

/// Set how an instance takes part in sync.
pub(crate) fn set_sync(
    instances_root: &Path,
    id: &InstanceId,
    sync_id: Option<String>,
    local_only: bool,
) -> Result<Instance, AshError> {
    let mut instance = read_metadata(instances_root, id)?;
    instance.sync_id = sync_id;
    instance.local_only = local_only;
    write_metadata(instances_root, &instance)?;
    Ok(instance)
}

pub(crate) fn rename(
    instances_root: &Path,
    id: &InstanceId,
    new_name: &str,
) -> Result<Instance, AshError> {
    let new_name = new_name.trim();
    if new_name.is_empty() {
        return Err(AshError::InvalidInstanceName { detail: "the name is empty".into() });
    }
    let mut instance = read_metadata(instances_root, id)?;
    // The id, and therefore every path, stays put. Only the label changes.
    instance.name = new_name.to_owned();
    write_metadata(instances_root, &instance)?;
    Ok(instance)
}

pub(crate) fn mark_played(instances_root: &Path, id: &InstanceId) -> Result<Instance, AshError> {
    let mut instance = read_metadata(instances_root, id)?;
    instance.last_played_ms = Some(now_ms());
    write_metadata(instances_root, &instance)?;
    Ok(instance)
}

/// Start a session at `started_ms`.
///
/// A session still open from before is closed first. Nothing was seen to end
/// it, which happens when the launcher was closed while the game ran, so it is
/// closed when the game last wrote its log.
pub(crate) fn begin_session(
    instances_root: &Path,
    id: &InstanceId,
    started_ms: u64,
) -> Result<Instance, AshError> {
    let mut instance = read_metadata(instances_root, id)?;
    if let Some(Session { started_ms: before, ended_ms: None }) = instance.last_session {
        close(&mut instance, before, unseen_end(instances_root, id, before));
    }
    instance.last_session = Some(Session { started_ms, ended_ms: None });
    instance.last_played_ms = Some(started_ms);
    write_metadata(instances_root, &instance)?;
    Ok(instance)
}

/// End the session that began at `started_ms`, if it is still the open one.
///
/// Matched by its start, so news of an old game ending can never close a
/// newer session.
pub(crate) fn end_session(
    instances_root: &Path,
    id: &InstanceId,
    started_ms: u64,
    ended_ms: u64,
) -> Result<(), AshError> {
    let mut instance = read_metadata(instances_root, id)?;
    if instance.last_session == Some(Session { started_ms, ended_ms: None }) {
        close(&mut instance, started_ms, ended_ms);
        write_metadata(instances_root, &instance)?;
    }
    Ok(())
}

fn close(instance: &mut Instance, started_ms: u64, ended_ms: u64) {
    // A clock set back mid-session would otherwise make the session negative.
    let ended_ms = ended_ms.max(started_ms);
    instance.played_ms += ended_ms - started_ms;
    instance.last_session = Some(Session { started_ms, ended_ms: Some(ended_ms) });
}

/// When a session nobody saw end most likely ended: the last time the game
/// wrote `logs/latest.log`, which both version targets write to until they
/// exit. The session's start if that log is missing or older than it, so a
/// session ash cannot account for adds no time rather than a guess.
pub(crate) fn unseen_end(instances_root: &Path, id: &InstanceId, started_ms: u64) -> u64 {
    fs::metadata(game_dir(instances_root, id).join("logs").join("latest.log"))
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map_or(started_ms, |written| (written.as_millis() as u64).max(started_ms))
}

/// One of ash's own jars as Phase 2 left it in the mods folder: its exact
/// file name, and the SHA-1 its bytes must have for it to be ash's.
pub(crate) struct LeftBehind {
    pub(crate) file_name: String,
    /// `None` for ash's client, which ships in the installer with no pinned
    /// hash, so its fixed name is all there is to know it by.
    pub(crate) sha1: Option<String>,
}

/// Remove the jars Phase 2 copied into the mods folder, and nothing else.
///
/// The mods folder is the player's now: the loader is handed ash's jars by
/// path. A file is removed only when its name is exactly one ash wrote and,
/// for a bundled mod, its bytes are exactly the pinned ones. A player's own
/// download of the same mod, under the same name or any other, is not ash's
/// to touch. Returns the names removed.
pub(crate) fn remove_left_behind(
    instances_root: &Path,
    id: &InstanceId,
    left_behind: &[LeftBehind],
) -> Result<Vec<String>, AshError> {
    let mods = game_dir(instances_root, id).join("mods");
    let mut removed = Vec::new();
    for jar in left_behind {
        let path = mods.join(&jar.file_name);
        if !path.is_file() {
            continue;
        }
        let ours = match &jar.sha1 {
            None => true,
            Some(pinned) => fs::read(&path)
                .map(|bytes| sha1_hex(&bytes).eq_ignore_ascii_case(pinned))
                .unwrap_or(false),
        };
        if ours {
            fs::remove_file(&path)
                .map_err(storage_err("removing ash's jar from the mods folder"))?;
            removed.push(jar.file_name.clone());
        }
    }
    Ok(removed)
}

fn sha1_hex(bytes: &[u8]) -> String {
    use sha1::{Digest, Sha1};
    Sha1::digest(bytes).iter().map(|b| format!("{b:02x}")).collect()
}

fn dir_entry_names(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = fs::read_dir(dir)
        .map(|entries| entries.flatten().filter_map(|e| e.file_name().into_string().ok()).collect())
        .unwrap_or_default();
    names.sort();
    names
}

fn size_on_disk(dir: &Path) -> u64 {
    let Ok(entries) = fs::read_dir(dir) else {
        return 0;
    };
    entries
        .flatten()
        .map(|e| match e.metadata() {
            Ok(m) if m.is_dir() => size_on_disk(&e.path()),
            Ok(m) => m.len(),
            Err(_) => 0,
        })
        .sum()
}

pub(crate) fn preview_deletion(
    instances_root: &Path,
    id: &InstanceId,
) -> Result<DeletionPreview, AshError> {
    let instance = read_metadata(instances_root, id)?;
    let game = game_dir(instances_root, id);

    Ok(DeletionPreview {
        instance,
        worlds: dir_entry_names(&game.join("saves")),
        resource_packs: dir_entry_names(&game.join("resourcepacks")).len(),
        screenshots: dir_entry_names(&game.join("screenshots")).len(),
        total_bytes: size_on_disk(&instance_dir(instances_root, id)),
    })
}

/// Remove an instance and everything inside it.
///
/// Scoped to this instance's own directory and nothing else. ADR-0008 makes
/// the depot shared, so deleting an instance must never reach into it - the
/// player would silently lose game files every other instance depends on.
pub(crate) fn delete(instances_root: &Path, id: &InstanceId) -> Result<(), AshError> {
    let dir = instance_dir(instances_root, id);
    if !dir.exists() {
        return Err(AshError::InstanceNotFound { id: id.to_string() });
    }
    fs::remove_dir_all(&dir).map_err(storage_err("deleting the instance"))
}
