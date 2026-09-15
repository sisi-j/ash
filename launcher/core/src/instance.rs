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
        created_at_ms: now_ms(),
        last_played_ms: None,
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

/// Put a bundled mod where the loader will find it.
///
/// Copied out of the depot rather than fetched again: the depot already has
/// it verified, and two instances on one version target share those bytes.
///
/// An ash release that moves a pin has to *replace* the jar rather than sit
/// beside it. A loader refuses to start when two files claim one mod id, so
/// leaving the previous version behind would turn an ash update into a game
/// that no longer launches - and the player would have no reason to connect
/// the two.
///
/// Removal is scoped to this artifact's own file names. A player's own mods,
/// worlds and resource packs share this directory tree and are not ash's to
/// manage; preparing an instance must never touch them.
pub(crate) fn install_bundled_mod(
    instances_root: &Path,
    id: &InstanceId,
    source: &Path,
    artifact: &str,
    file_name: &str,
) -> Result<(), AshError> {
    let mods = game_dir(instances_root, id).join("mods");
    fs::create_dir_all(&mods).map_err(storage_err("creating the mods directory"))?;

    let prefix = format!("{artifact}-");
    for entry in fs::read_dir(&mods).into_iter().flatten().flatten() {
        let Ok(name) = entry.file_name().into_string() else {
            continue;
        };
        if name != file_name && name.starts_with(&prefix) && name.ends_with(".jar") {
            let _ = fs::remove_file(entry.path());
        }
    }

    fs::copy(source, mods.join(file_name))
        .map(|_| ())
        .map_err(storage_err("installing a bundled mod"))
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
