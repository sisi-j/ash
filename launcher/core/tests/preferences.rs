//! Launcher preferences: the launcher's own settings, not any one instance's.
//!
//! Launch sounds is the first. What these tests guard is that a choice the
//! player made is still their choice the next time ash opens, and that a
//! file ash cannot read never stops the launcher from starting.

use std::path::Path;
use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{Ash, Config, LauncherPreferences};

fn ash_at(base: &Path) -> Ash {
    Ash::new(
        Config::rooted_at(base),
        FakeHttp::new() as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    )
}

/// Every file under a directory, so a test can say where a preference went.
fn files_under(root: &Path) -> Vec<std::path::PathBuf> {
    let mut found = Vec::new();
    let mut pending = vec![root.to_path_buf()];
    while let Some(dir) = pending.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else { continue };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                pending.push(path);
            } else {
                found.push(path);
            }
        }
    }
    found
}

#[test]
fn launch_sounds_are_on_until_the_player_turns_them_off() {
    let tmp = tempfile::tempdir().expect("temp dir");

    assert!(ash_at(tmp.path()).launcher_preferences().launch_sounds);
}

#[test]
fn a_preference_is_still_set_the_next_time_ash_opens() {
    let tmp = tempfile::tempdir().expect("temp dir");
    let quiet = LauncherPreferences { launch_sounds: false };

    let saved = ash_at(tmp.path()).set_launcher_preferences(quiet.clone()).expect("saved");

    assert_eq!(saved, quiet);
    assert_eq!(
        ash_at(tmp.path()).launcher_preferences(),
        quiet,
        "a new Ash on the same data reads it back"
    );
}

#[test]
fn a_file_ash_cannot_read_gives_the_defaults_and_is_replaced_on_the_next_save() {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = ash_at(tmp.path());
    ash.set_launcher_preferences(LauncherPreferences { launch_sounds: false }).expect("saved");
    let files = files_under(&Config::rooted_at(tmp.path()).data_root);
    // The test proves nothing if the save wrote no file to corrupt.
    assert_eq!(files.len(), 1, "one preferences file, found {files:?}");
    std::fs::write(&files[0], b"{ not json").expect("corrupt it");

    assert_eq!(ash.launcher_preferences(), LauncherPreferences::default());

    ash.set_launcher_preferences(LauncherPreferences { launch_sounds: false })
        .expect("saved again");
    assert!(!ash.launcher_preferences().launch_sounds);
}

#[test]
fn preferences_live_with_ash_s_own_state_never_inside_an_instance() {
    let tmp = tempfile::tempdir().expect("temp dir");
    let config = Config::rooted_at(tmp.path());

    ash_at(tmp.path())
        .set_launcher_preferences(LauncherPreferences { launch_sounds: false })
        .expect("saved");

    assert_eq!(files_under(&config.data_root).len(), 1);
    assert!(files_under(&config.instances_root).is_empty());
}
