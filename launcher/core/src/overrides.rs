//! Machine-local overrides.
//!
//! A machine-local override is a value that only makes sense on one machine:
//! how much memory to give the JVM, which Java to use, what size to open the
//! window. Phase 4 syncs an account's launcher state between machines, and a
//! memory figure from a 32GB desktop arriving on an 8GB laptop is a game that
//! will not start.
//!
//! The boundary is storage, not discipline. These live under the data root -
//! ash's own state for *this* machine - and never inside `instances/`, which
//! is the directory an instance is, the one a sync or a backup or a copy to
//! another machine would carry. A future sync cannot leak one of these
//! values because it would have to go looking somewhere else entirely to
//! find it, and [`crate::Instance`] holds no field it could travel in.

use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::instance::InstanceId;

/// Where machine-local state lives, relative to the data root.
const MACHINE_DIR: &str = "machine";

/// What ash gives the JVM when the player has not said.
///
/// Mojang's own launcher uses the same figure. Deliberately modest: guessing
/// high costs a player with 8GB their whole machine, and guessing low costs
/// a player with 32GB one trip to this screen.
pub const DEFAULT_MEMORY_MB: u32 = 2048;

/// Below this the game will not start; above it, the player is almost
/// certainly typing megabytes when they meant gigabytes.
const MEMORY_RANGE: std::ops::RangeInclusive<u32> = 512..=65536;
const DIMENSION_RANGE: std::ops::RangeInclusive<u32> = 320..=15360;

/// The window size to open the game at.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Resolution {
    pub width: u32,
    pub height: u32,
}

/// Settings that belong to this machine and must never leave it.
///
/// Every field is optional, and `None` means "whatever ash would do anyway".
/// That distinction is worth keeping: a player who has never opened this
/// screen should follow ash's defaults as they change, not be pinned to
/// whatever the default happened to be the day they created the instance.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct MachineOverrides {
    /// Maximum JVM heap, in megabytes.
    #[serde(default)]
    pub memory_mb: Option<u32>,
    /// A Java binary the player chose. Absent means the one ash provisioned.
    #[serde(default)]
    pub java_executable: Option<PathBuf>,
    #[serde(default)]
    pub resolution: Option<Resolution>,
}

impl MachineOverrides {
    /// The heap size to actually pass, defaulted.
    pub fn memory_mb_or_default(&self) -> u32 {
        self.memory_mb.unwrap_or(DEFAULT_MEMORY_MB)
    }

    /// Reject anything that would produce a JVM that cannot start.
    ///
    /// Checked when it is set rather than when the game is launched, so the
    /// player finds out while looking at the field they just typed in.
    pub fn validate(&self) -> Result<(), AshError> {
        if let Some(memory) = self.memory_mb {
            if !MEMORY_RANGE.contains(&memory) {
                return Err(AshError::InvalidSetting {
                    detail: format!(
                        "memory must be between {} and {} MB",
                        MEMORY_RANGE.start(),
                        MEMORY_RANGE.end()
                    ),
                });
            }
        }

        if let Some(resolution) = self.resolution {
            for value in [resolution.width, resolution.height] {
                if !DIMENSION_RANGE.contains(&value) {
                    return Err(AshError::InvalidSetting {
                        detail: format!(
                            "window size must be between {} and {} pixels",
                            DIMENSION_RANGE.start(),
                            DIMENSION_RANGE.end()
                        ),
                    });
                }
            }
        }

        if let Some(java) = &self.java_executable {
            // Checked now, because the alternative is a spawn failure with
            // nothing in it that points at this setting.
            if !java.is_file() {
                return Err(AshError::InvalidSetting {
                    detail: "that Java path is not a file".into(),
                });
            }
        }

        Ok(())
    }
}

// ---- storage ---------------------------------------------------------------

fn path(data_root: &Path, id: &InstanceId) -> PathBuf {
    data_root.join(MACHINE_DIR).join(format!("{id}.json"))
}

/// Read an instance's overrides. Absent, unreadable or corrupt all mean the
/// same thing: this machine has nothing to say, so use ash's defaults.
pub(crate) fn load(data_root: &Path, id: &InstanceId) -> MachineOverrides {
    fs::read(path(data_root, id))
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok())
        .unwrap_or_default()
}

pub(crate) fn save(
    data_root: &Path,
    id: &InstanceId,
    overrides: &MachineOverrides,
) -> Result<(), AshError> {
    let file = path(data_root, id);
    let dir = file.parent().expect("the machine path always has a parent");
    fs::create_dir_all(dir).map_err(|e| AshError::Storage {
        detail: format!("creating the machine settings directory: {e}"),
    })?;

    let encoded = serde_json::to_vec_pretty(overrides)
        .map_err(|e| AshError::Storage { detail: format!("encoding machine settings: {e}") })?;
    fs::write(&file, encoded)
        .map_err(|e| AshError::Storage { detail: format!("writing machine settings: {e}") })
}

/// Forget an instance's overrides.
///
/// Called when the instance is deleted. Because these do not live in the
/// instance directory, removing that directory does not remove these - and
/// an orphan would be inherited by the next instance that happened to take
/// the same id.
pub(crate) fn forget(data_root: &Path, id: &InstanceId) {
    let _ = fs::remove_file(path(data_root, id));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_unset_memory_figure_falls_back_to_ashs_default() {
        assert_eq!(MachineOverrides::default().memory_mb_or_default(), DEFAULT_MEMORY_MB);
        let chosen = MachineOverrides { memory_mb: Some(8192), ..Default::default() };
        assert_eq!(chosen.memory_mb_or_default(), 8192);
    }

    #[test]
    fn memory_outside_what_a_jvm_will_take_is_refused() {
        for memory in [0, 128, 500, 131_072] {
            let overrides = MachineOverrides { memory_mb: Some(memory), ..Default::default() };
            assert!(overrides.validate().is_err(), "{memory} MB should be refused");
        }
        for memory in [512, 2048, 65536] {
            let overrides = MachineOverrides { memory_mb: Some(memory), ..Default::default() };
            assert!(overrides.validate().is_ok(), "{memory} MB should be allowed");
        }
    }

    #[test]
    fn a_window_too_small_to_render_is_refused() {
        let overrides = MachineOverrides {
            resolution: Some(Resolution { width: 10, height: 10 }),
            ..Default::default()
        };
        assert!(overrides.validate().is_err());
    }

    #[test]
    fn a_java_path_that_is_not_there_is_refused_while_the_player_is_looking() {
        let overrides = MachineOverrides {
            java_executable: Some(PathBuf::from("nowhere/java.exe")),
            ..Default::default()
        };
        let err = overrides.validate().expect_err("no such file");
        assert_eq!(err.kind(), "invalid_setting");
    }

    #[test]
    fn defaults_are_valid() {
        assert!(MachineOverrides::default().validate().is_ok());
    }
}
