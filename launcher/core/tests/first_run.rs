//! The first run (spec 0005): three instances ready after the first sign-in,
//! when the player has none.

use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, Loader};

fn ash(root: &std::path::Path) -> Ash {
    Ash::new(
        Config::rooted_at(root),
        FakeHttp::new() as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    )
}

#[test]
fn a_player_with_no_instances_gets_one_for_each_version_target() {
    let tmp = tempfile::tempdir().unwrap();
    let ash = ash(tmp.path());

    let made = ash.make_first_run_instances().unwrap();

    let what: Vec<(&str, &str, Loader)> =
        made.iter().map(|i| (i.name.as_str(), i.version_id.as_str(), i.loader)).collect();
    assert_eq!(
        what,
        [
            ("1.21.11", "1.21.11", Loader::Fabric),
            ("1.20.4", "1.20.4", Loader::Fabric),
            ("1.8.9", "1.8.9", Loader::LegacyFabric)
        ]
    );
    assert_eq!(ash.instances().unwrap().len(), 3);
}

#[test]
fn it_is_asked_once_so_deleting_them_all_doesn_t_bring_them_back() {
    let tmp = tempfile::tempdir().unwrap();
    let ash = ash(tmp.path());
    for instance in ash.make_first_run_instances().unwrap() {
        ash.delete_instance(&instance.id).unwrap();
    }

    assert!(ash.make_first_run_instances().unwrap().is_empty());
    assert!(ash.instances().unwrap().is_empty());
}

#[test]
fn a_player_who_has_instances_already_gets_none_added() {
    let tmp = tempfile::tempdir().unwrap();
    let ash = ash(tmp.path());
    ash.create_instance("mine", "1.21.11", Loader::Vanilla).unwrap();

    assert!(ash.make_first_run_instances().unwrap().is_empty());
    assert_eq!(ash.instances().unwrap().len(), 1);
}
