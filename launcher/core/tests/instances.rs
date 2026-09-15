//! Instance behaviour: isolation, lifecycle, and what deletion destroys.
//!
//! No network here at all - `FakeHttp` with no routes panics on any request,
//! so these tests prove instance work never reaches for Mojang.

use std::fs;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::FakeHttp;
use ash_core::process::FakeProcessPort;
use ash_core::{Ash, Config, Loader, MachineOverrides};

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

    let instance = ash.create_instance("Ranked 1.8.9", "1.8.9", Loader::Vanilla).unwrap();

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

    let legacy = ash.create_instance("pvp", "1.8.9", Loader::Vanilla).unwrap();
    let modern = ash.create_instance("smp", "1.21.4", Loader::Vanilla).unwrap();

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

    let first = ash.create_instance("pvp", "1.8.9", Loader::Vanilla).unwrap();
    let second = ash.create_instance("pvp", "1.8.9", Loader::Vanilla).unwrap();

    assert_ne!(first.id, second.id);
    assert_ne!(ash.game_directory(&first.id), ash.game_directory(&second.id));
    assert_eq!(first.name, second.name, "the display name is free to collide");
}

#[test]
fn a_name_with_no_ascii_characters_still_produces_a_usable_instance() {
    let (ash, _tmp) = ash();

    let instance = ash.create_instance("私の世界", "1.21.4", Loader::Vanilla).unwrap();

    // The display name is preserved even though the id cannot be derived
    // from it. User story 64 depends on this not blowing up.
    assert_eq!(instance.name, "私の世界");
    assert!(!instance.id.as_str().is_empty());
    assert!(ash.game_directory(&instance.id).join("saves").is_dir());
}

#[test]
fn an_empty_name_is_refused() {
    let (ash, _tmp) = ash();

    let err =
        ash.create_instance("   ", "1.21.4", Loader::Vanilla).expect_err("empty names are refused");

    assert_eq!(err.kind(), "invalid_instance_name");
    assert!(ash.instances().unwrap().is_empty(), "nothing was created");
}

#[test]
fn renaming_keeps_the_directory_where_it_was() {
    let (ash, _tmp) = ash();
    let instance = ash.create_instance("old name", "1.21.4", Loader::Vanilla).unwrap();
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
    let a = ash.create_instance("a", "1.21.4", Loader::Vanilla).unwrap();
    let b = ash.create_instance("b", "1.8.9", Loader::Vanilla).unwrap();

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
    let instance = ash.create_instance("smp", "1.21.4", Loader::Vanilla).unwrap();
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

    let doomed = ash.create_instance("doomed", "1.21.4", Loader::Vanilla).unwrap();
    let keeper = ash.create_instance("keeper", "1.8.9", Loader::Vanilla).unwrap();

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
    let instance = ash.create_instance("gone", "1.21.4", Loader::Vanilla).unwrap();
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

// ---- loaders ---------------------------------------------------------------

/// What ash wrote into `instance.json` before an instance had a loader.
///
/// Built here rather than checked in, and deliberately frozen: this is the
/// shape on a player's disk today, and a fixture regenerated from the current
/// writer would stop being the thing under test the moment the writer changed.
const PRE_LOADER_METADATA: &str = r#"{
  "id": "ranked",
  "name": "Ranked",
  "version_id": "1.8.9",
  "created_at_ms": 1757000000000,
  "last_played_ms": 1757000900000
}"#;

/// The machine-local overrides beside it, in the same pre-loader shape.
///
/// Written by hand for the same reason: these live under the data root, keyed
/// by instance id, and the point is that an upgrade still finds them.
const PRE_LOADER_OVERRIDES: &str = r#"{"memory_mb":24576}"#;

#[test]
fn an_instance_records_the_loader_it_was_created_with() {
    let (ash, tmp) = ash();

    let instance = ash.create_instance("Ranked", "1.8.9", Loader::Vanilla).unwrap();

    assert_eq!(instance.loader, Loader::Vanilla);
    // Read back rather than trusted from the value creation returned: the
    // choice has to survive ash being closed, which means it has to be on
    // disk and not only in the struct that came out of the constructor.
    assert_eq!(ash.instance(&instance.id).unwrap().loader, Loader::Vanilla);

    let raw = fs::read_to_string(
        tmp.path().join("instances").join(instance.id.as_str()).join("instance.json"),
    )
    .unwrap();
    assert!(raw.contains("\"loader\""), "the loader is not recorded with the instance: {raw}");
}

#[test]
fn an_instance_from_before_loaders_reads_back_as_vanilla() {
    let (ash, tmp) = ash();
    let dir = tmp.path().join("instances").join("ranked");
    let game = dir.join("minecraft");
    fs::create_dir_all(game.join("saves").join("Old World")).unwrap();
    fs::create_dir_all(game.join("resourcepacks")).unwrap();
    fs::write(game.join("resourcepacks").join("pack.zip"), b"pack").unwrap();
    fs::write(dir.join("instance.json"), PRE_LOADER_METADATA).unwrap();

    let machine = tmp.path().join("data").join("machine");
    fs::create_dir_all(&machine).unwrap();
    fs::write(machine.join("ranked.json"), PRE_LOADER_OVERRIDES).unwrap();

    // The test proves nothing if the fixture already names a loader - the
    // whole question is what happens when the field is absent.
    assert!(!PRE_LOADER_METADATA.contains("loader"));

    let listed = ash.instances().unwrap();
    assert_eq!(listed.len(), 1, "the instance that predates loaders is still listed");
    let instance = &listed[0];

    // Vanilla is a loader rather than the absence of one, so an instance
    // written before the field existed is not a special case - it is a
    // vanilla instance, and everything downstream can treat it as one.
    assert_eq!(instance.loader, Loader::Vanilla);
    assert_eq!(instance.name, "Ranked");
    assert_eq!(instance.version_id, "1.8.9");
    assert_eq!(instance.created_at_ms, 1_757_000_000_000);
    assert_eq!(instance.last_played_ms, Some(1_757_000_900_000));

    let preview = ash.preview_deletion(&instance.id).unwrap();
    assert_eq!(preview.worlds, vec!["Old World"], "the player's worlds are untouched");
    assert_eq!(preview.resource_packs, 1);

    let overrides = ash.overrides(&instance.id).unwrap();
    assert_eq!(overrides.memory_mb, Some(24576), "machine-local overrides still apply");
}

#[test]
fn the_recorded_loader_survives_every_write_to_an_instance() {
    let (ash, tmp) = ash();
    let instance = ash.create_instance("pvp", "1.8.9", Loader::Vanilla).unwrap();
    let metadata = tmp.path().join("instances").join(instance.id.as_str()).join("instance.json");

    // Everything ash offers that writes an instance back to disk. None of
    // them takes a loader - the choice is fixed at creation - so what this
    // guards is the field being dropped on the way through a rewrite.
    ash.rename_instance(&instance.id, "pvp renamed").unwrap();
    ash.mark_played(&instance.id).unwrap();
    ash.set_overrides(
        &instance.id,
        MachineOverrides { memory_mb: Some(4096), ..Default::default() },
    )
    .unwrap();

    let raw = fs::read_to_string(&metadata).unwrap();
    assert!(raw.contains("\"loader\""), "a rewrite dropped the loader: {raw}");
    assert_eq!(ash.instance(&instance.id).unwrap().loader, instance.loader);
}
