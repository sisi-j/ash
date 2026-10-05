//! Launcher preferences.
//!
//! The launcher's own settings, as opposed to any one instance's: whether
//! LAUNCH GAME makes a sound, today, and the rest of the launcher's Settings
//! page as it arrives. Kept under the data root with ash's other state of
//! its own, never inside `instances/`.
//!
//! Unlike a [`crate::MachineOverrides`] value, a preference here means the
//! same on any machine, which is why the glossary lists launcher preferences
//! among what synced settings will carry.

use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::AshError;

const FILE: &str = "launcher-preferences.json";

/// The launcher's own settings.
///
/// Every field has a serde default, so a file written before a field existed
/// still loads, with that one field at its default.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LauncherPreferences {
    /// A short sound when LAUNCH GAME is clicked and a quieter one when the
    /// game starts. On unless the player turns it off.
    #[serde(default = "on")]
    pub launch_sounds: bool,
}

fn on() -> bool {
    true
}

impl Default for LauncherPreferences {
    fn default() -> Self {
        Self { launch_sounds: on() }
    }
}

fn path(data_root: &Path) -> PathBuf {
    data_root.join(FILE)
}

/// Absent, unreadable or corrupt all mean the same: the player has said
/// nothing ash can read, so use the defaults. A preference never stops the
/// launcher from opening.
pub(crate) fn load(data_root: &Path) -> LauncherPreferences {
    fs::read(path(data_root))
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok())
        .unwrap_or_default()
}

pub(crate) fn save(data_root: &Path, preferences: &LauncherPreferences) -> Result<(), AshError> {
    fs::create_dir_all(data_root).map_err(AshError::writing("creating ash's data directory"))?;
    let encoded = serde_json::to_vec_pretty(preferences)
        .map_err(|e| AshError::Storage { detail: format!("encoding launcher preferences: {e}") })?;
    fs::write(path(data_root), encoded).map_err(AshError::writing("writing launcher preferences"))
}
