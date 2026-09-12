//! Instance behaviour: isolation, lifecycle, and what deletion destroys.
//!
//! No network here at all - `FakeHttp` with no routes panics on any request,
//! so these tests prove instance work never reaches for Mojang.

use std::fs;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::process::FakeProcessPort;
use ash_core::http::FakeHttp;
use ash_core::{Ash, Config};

fn ash() -> (Ash, tempfile::TempDir) {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        FakeHttp::new(),
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        "test-client",
    );
    (ash, tmp)
}

#[test]
fn creates_an_instance_with_its_own_game_directory() {
    let (ash, _tmp) = ash();

    let instance = ash.create_instance("Ranked 1.8.9", "1.8.9").unwrap();

    assert_eq!(instance.name, "Ranked 1.8.9");
    assert_eq!(instance.version_id, "1.8.9");
    assert_eq!(instance.last_played_ms, None);

    let game = ash.game_directory(&instance.id);
    for sub in ["saves", "config", "resourcepacks", "screenshots", "mods"] {
        assert!(game.join(sub).is_dir(), "missing {sub} in the game directory");
    }
}

#[test]
fn two_instances_share_no_files_in_their_game_directories() {
    let (ash, _tmp) = ash();

    let legacy = ash.create_instance("pvp", "1.8.9").unwrap();
    let modern = ash.create_instance("smp", "1.21.4").unwrap();

    let legacy_game = ash.game_directory(&legacy.id);
    let modern_game = ash.game_directory(&modern.id);
    assert_ne!(legacy_game, modern_game);

    // A 1.8.9 pack and a 1.21 pack use incompatible formats. Dropping one in
    // must not make it visible to the other - this is the whole of ADR-0008.
    fs::write(legacy_game.join("resourcepacks").join("pack.zip"), b"legacy").unwrap();

    assert!(legacy_game.join("resourcepacks").join("pack.zip").exists());
    assert!(!modern_game.join("resourcepacks").join("pack.zip").exists());
}

#[test]
fn instances_with_the_same_name_get_distinct_directories() {
    let (ash, _tmp) = ash();

    let first = ash.create_instance("pvp", "1.8.9").unwrap();
    let second = ash.create_instance("pvp", "1.8.9").unwrap();

    assert_ne!(first.id, second.id);
    assert_ne!(ash.game_directory(&first.id), ash.game_directory(&second.id));
    assert_eq!(first.name, second.name, "the display name is free to collide");
}

#[test]
fn a_name_with_no_ascii_characters_still_produces_a_usable_instance() {
    let (ash, _tmp) = ash();

    let instance = ash.create_instance("私の世界", "1.21.4").unwrap();

    // The display name is preserved even though the id cannot be derived
    // from it. User story 64 depends on this not blowing up.
    assert_eq!(instance.name, "私の世界");
    assert!(!instance.id.as_str().is_empty());
    assert!(ash.game_directory(&instance.id).join("saves").is_dir());
}

#[test]
fn an_empty_name_is_refused() {
    let (ash, _tmp) = ash();

    let err = ash.create_instance("   ", "1.21.4").expect_err("empty names are refused");

    assert_eq!(err.kind(), "invalid_instance_name");
    assert!(ash.instances().unwrap().is_empty(), "nothing was created");
}

#[test]
fn renaming_keeps_the_directory_where_it_was() {
    let (ash, _tmp) = ash();
    let instance = ash.create_instance("old name", "1.21.4").unwrap();
    let before = ash.game_directory(&instance.id);

    let renamed = ash.rename_instance(&instance.id, "new name").unwrap();

    assert_eq!(renamed.name, "new name");
    assert_eq!(renamed.id, instance.id);
    // Paths end up in JVM arguments and the player's own shortcuts; a rename
    // that moved them would break both.
    assert_eq!(ash.game_directory(&renamed.id), before);
    assert!(before.is_dir());
}

#[test]
fn instances_are_listed_most_recently_played_first() {
    let (ash, _tmp) = ash();
    let a = ash.create_instance("a", "1.21.4").unwrap();
    let b = ash.create_instance("b", "1.8.9").unwrap();

    ash.mark_played(&a.id).unwrap();

    let listed: Vec<String> = ash.instances().unwrap().into_iter().map(|i| i.name).collect();
    assert_eq!(listed, vec!["a", "b"], "played instance comes first");

    ash.mark_played(&b.id).unwrap();
    let listed: Vec<String> = ash.instances().unwrap().into_iter().map(|i| i.name).collect();
    assert_eq!(listed, vec!["b", "a"]);
}

#[test]
fn deletion_preview_names_the_worlds_that_would_be_lost() {
    let (ash, _tmp) = ash();
    let instance = ash.create_instance("smp", "1.21.4").unwrap();
    let game = ash.game_directory(&instance.id);

    fs::create_dir_all(game.join("saves").join("Hardcore Run")).unwrap();
    fs::create_dir_all(game.join("saves").join("Creative Test")).unwrap();
    fs::write(game.join("resourcepacks").join("pack.zip"), b"pack").unwrap();
    fs::write(game.join("screenshots").join("shot.png"), b"png").unwrap();

    let preview = ash.preview_deletion(&instance.id).unwrap();

    // Named, not counted: "2 worlds" is not enough to decide with.
    assert_eq!(preview.worlds, vec!["Creative Test", "Hardcore Run"]);
    assert_eq!(preview.resource_packs, 1);
    assert_eq!(preview.screenshots, 1);
    assert!(preview.total_bytes > 0);
}

#[test]
fn deleting_an_instance_leaves_the_depot_and_other_instances_alone() {
    let tmp = tempfile::tempdir().unwrap();
    let config = Config::rooted_at(tmp.path());
    let depot_root = config.depot_root.clone();
    let ash = Ash::new(
        config,
        FakeHttp::new(),
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        "test-client",
    );

    fs::create_dir_all(&depot_root).unwrap();
    let shared_jar = depot_root.join("client-1.21.4.jar");
    fs::write(&shared_jar, b"shared game files").unwrap();

    let doomed = ash.create_instance("doomed", "1.21.4").unwrap();
    let keeper = ash.create_instance("keeper", "1.8.9").unwrap();

    ash.delete_instance(&doomed.id).unwrap();

    assert!(shared_jar.exists(), "the depot is shared - deletion must not reach into it");
    assert!(ash.game_directory(&keeper.id).is_dir(), "other instances keep working");
    assert!(!ash.game_directory(&doomed.id).exists());

    let remaining: Vec<String> = ash.instances().unwrap().into_iter().map(|i| i.name).collect();
    assert_eq!(remaining, vec!["keeper"]);
}

#[test]
fn operating_on_a_missing_instance_is_a_typed_error() {
    let (ash, _tmp) = ash();
    let instance = ash.create_instance("gone", "1.21.4").unwrap();
    ash.delete_instance(&instance.id).unwrap();

    for err in [
        ash.instance(&instance.id).unwrap_err(),
        ash.rename_instance(&instance.id, "x").unwrap_err(),
        ash.preview_deletion(&instance.id).unwrap_err(),
        ash.delete_instance(&instance.id).unwrap_err(),
    ] {
        assert_eq!(err.kind(), "instance_not_found");
        let message = err.user_message();
        assert!(!message.contains('\\'), "leaked a path: {message}");
        assert!(!message.contains('/'), "leaked a path: {message}");
    }
}

#[test]
fn no_instances_before_any_are_created() {
    let (ash, _tmp) = ash();
    assert!(ash.instances().unwrap().is_empty());
}
