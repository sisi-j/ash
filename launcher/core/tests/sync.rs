//! Synced settings (spec 0004, ADR-0022, research 0011), between two machines
//! signed in to one account, through a fake of ash's backend that keeps the
//! account's settings as the real one does: the latest change per key wins.

use std::collections::BTreeMap;
use std::path::Path;
use std::sync::{Arc, Mutex};

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpPort, HttpRequest, HttpResponse, Method};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, AshError, Config, Instance, Loader, SyncState};
use async_trait::async_trait;

mod common;

const BACKEND: &str = "https://api.ash.test";
const SETTINGS_URL: &str = "https://api.ash.test/v1/settings";

// ---- a backend that keeps settings ------------------------------------------------

#[derive(Default)]
struct Backend {
    settings: Mutex<BTreeMap<String, serde_json::Value>>,
    down: Mutex<bool>,
    /// Every PUT's changes, in order.
    sent: Mutex<Vec<serde_json::Value>>,
}

struct Wire {
    backend: Arc<Backend>,
    rest: Arc<FakeHttp>,
}

#[async_trait]
impl HttpPort for Wire {
    async fn send(&self, request: HttpRequest) -> Result<HttpResponse, AshError> {
        if !request.url.starts_with(SETTINGS_URL) {
            return self.rest.send(request).await;
        }
        if *self.backend.down.lock().unwrap() {
            return Err(AshError::Transport { url: request.url, detail: "down".into() });
        }
        let mut settings = self.backend.settings.lock().unwrap();
        if request.method == Method::Put {
            let body: serde_json::Value =
                serde_json::from_slice(request.body.as_deref().unwrap()).unwrap();
            for change in body["changes"].as_array().unwrap() {
                let key = change["key"].as_str().unwrap().to_owned();
                let newer = settings.get(&key).is_none_or(|stored| {
                    change["updated_at"].as_u64() > stored["updated_at"].as_u64()
                });
                if newer {
                    settings.insert(key, change.clone());
                }
            }
            self.backend.sent.lock().unwrap().push(body["changes"].clone());
        }
        let all: Vec<_> = settings.values().cloned().collect();
        Ok(HttpResponse::ok(serde_json::json!({ "settings": all }).to_string()))
    }
}

// ---- a machine ---------------------------------------------------------------------

struct Machine {
    ash: Ash,
    _tmp: tempfile::TempDir,
}

async fn machine(backend: &Arc<Backend>) -> Machine {
    let tmp = tempfile::tempdir().unwrap();
    let store = InMemoryCredentialStore::new();
    let http =
        Wire { backend: Arc::clone(backend), rest: common::with_auth_routes(FakeHttp::new()) };
    let ash = Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(tmp.path()) },
        Arc::new(http) as Arc<dyn HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    ash.begin_sign_in().await.unwrap();
    ash.poll_sign_in().await.unwrap();
    // Signed in to ash, as the background sign-in would have left it.
    store
        .set(
            &format!("ash-session:{}", common::PLAYER_UUID),
            r#"{"token":"T","expires_at":99999999999999}"#,
        )
        .unwrap();
    Machine { ash, _tmp: tmp }
}

impl Machine {
    async fn sync(&self) -> SyncState {
        self.ash.sync_settings().await.state
    }

    fn modded(&self, name: &str) -> Instance {
        self.ash.create_instance(name, "1.21.11", Loader::Fabric).unwrap()
    }

    fn settings_file(&self, instance: &Instance) -> std::path::PathBuf {
        self.ash.game_directory(&instance.id).join("config/ash.properties")
    }

    /// What the client does when the player changes a setting in game.
    fn change_in_game(&self, instance: &Instance, text: &str) {
        let file = self.settings_file(instance);
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        // Modification times are what tell a change; make sure this one is newer.
        std::thread::sleep(std::time::Duration::from_millis(20));
        std::fs::write(&file, text).unwrap();
    }

    fn setting(&self, instance: &Instance, key: &str) -> Option<String> {
        let text = std::fs::read_to_string(self.settings_file(instance)).ok()?;
        text.lines().rev().find_map(|l| l.strip_prefix(&format!("{key}=")).map(str::to_owned))
    }

    fn instance_named(&self, name: &str) -> Option<Instance> {
        self.ash.instances().unwrap().into_iter().find(|i| i.name == name)
    }
}

// ---- feature settings ----------------------------------------------------------------

#[tokio::test]
async fn a_setting_changed_in_game_on_one_machine_reaches_the_other() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let b = machine(&backend).await;
    let on_a = a.modded("PvP");
    assert!(matches!(a.sync().await, SyncState::Synced { .. }));

    a.change_in_game(&on_a, "# ash\ncrosshair.colour=#FF2FBF71\ncrosshair.size=6\n");
    a.sync().await;
    let on_b = b.modded("Mine");
    b.sync().await;

    assert_eq!(b.setting(&on_b, "crosshair.colour").as_deref(), Some("#FF2FBF71"));
    assert_eq!(b.setting(&on_b, "crosshair.size").as_deref(), Some("6"));
    // And A's instance arrived on B, with the settings too.
    let arrived = b.instance_named("PvP").expect("A's instance on B");
    assert_eq!(b.setting(&arrived, "crosshair.size").as_deref(), Some("6"));
}

#[tokio::test]
async fn a_fresh_machine_s_defaults_never_undo_the_account() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    a.sync().await;
    a.change_in_game(&on_a, "crosshair.size=6\n");
    a.sync().await;

    // B's game ran before B ever synced, and wrote the client's defaults.
    let b = machine(&backend).await;
    let on_b = b.modded("Mine");
    b.change_in_game(&on_b, "crosshair.size=4\nhit-colour.strength=50\n");
    b.sync().await;

    assert_eq!(
        b.setting(&on_b, "crosshair.size").as_deref(),
        Some("6"),
        "the account's, not B's default"
    );
    a.sync().await;
    assert_eq!(a.setting(&on_a, "crosshair.size").as_deref(), Some("6"));
    // A key the account lacked is added, from B.
    assert_eq!(a.setting(&on_a, "hit-colour.strength").as_deref(), Some("50"));
}

#[tokio::test]
async fn a_new_instance_starts_with_the_account_s_settings_and_its_defaults_don_t_win() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let first = a.modded("PvP");
    a.sync().await;
    a.change_in_game(&first, "crosshair.size=6\n");
    a.sync().await;

    let second = a.modded("Second");
    assert_eq!(
        a.setting(&second, "crosshair.size").as_deref(),
        Some("6"),
        "seeded when it was made"
    );

    // Its first game appends what the file lacked, and keeps what was there.
    a.change_in_game(&second, "crosshair.size=6\npanel.size=120\nfreelook.enabled=false\n");
    a.sync().await;
    let b = machine(&backend).await;
    let on_b = b.modded("Mine");
    b.sync().await;
    assert_eq!(b.setting(&on_b, "crosshair.size").as_deref(), Some("6"));
    assert_eq!(
        b.setting(&on_b, "freelook.enabled").as_deref(),
        Some("false"),
        "a key the account lacked"
    );
}

#[tokio::test]
async fn an_instance_whose_defaults_were_written_while_sync_was_off_never_undoes_the_account() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let first = a.modded("PvP");
    a.sync().await;
    a.change_in_game(&first, "crosshair.size=6\n");
    a.sync().await;

    // Made with sync off, so not seeded; its first game writes the defaults.
    a.ash.set_sync_enabled(false).unwrap();
    let second = a.modded("Second");
    a.change_in_game(&second, "crosshair.size=4\nsnaplook.enabled=false\n");
    a.ash.set_sync_enabled(true).unwrap();
    a.sync().await;

    assert_eq!(backend.settings.lock().unwrap()["feature:crosshair.size"]["value"], "6");
    assert_eq!(
        a.setting(&second, "crosshair.size").as_deref(),
        Some("6"),
        "the account's, applied"
    );
    assert_eq!(
        backend.settings.lock().unwrap()["feature:snaplook.enabled"]["value"],
        "false",
        "a key the account lacked"
    );
}

#[tokio::test]
async fn the_latest_change_wins_key_by_key() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    a.change_in_game(&on_a, "crosshair.size=4\ncrosshair.gap=0\n");
    a.sync().await;
    let b = machine(&backend).await;
    b.sync().await;
    let on_b = b.instance_named("PvP").unwrap();

    a.change_in_game(&on_a, "crosshair.size=8\ncrosshair.gap=0\n");
    b.change_in_game(&on_b, "crosshair.size=4\ncrosshair.gap=3\n");
    a.sync().await;
    b.sync().await;
    a.sync().await;

    for (m, i) in [(&a, &on_a), (&b, &on_b)] {
        assert_eq!(m.setting(i, "crosshair.size").as_deref(), Some("8"), "A's change");
        assert_eq!(m.setting(i, "crosshair.gap").as_deref(), Some("3"), "B's change");
    }
}

#[tokio::test]
async fn interface_size_stays_on_each_machine() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    a.sync().await;
    a.change_in_game(&on_a, "panel.size=140\ncrosshair.size=5\n");
    a.sync().await;

    let sent = backend.settings.lock().unwrap();
    assert!(sent.contains_key("feature:crosshair.size"));
    assert!(!sent.contains_key("feature:panel.size"), "panel.size suits a screen, not an account");
}

// ---- launcher preferences --------------------------------------------------------------

#[tokio::test]
async fn launcher_preferences_follow_the_account() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    a.sync().await;
    let mut prefs = a.ash.launcher_preferences();
    prefs.launch_sounds = false;
    a.ash.set_launcher_preferences(prefs).unwrap();
    a.sync().await;

    let b = machine(&backend).await;
    assert!(b.ash.launcher_preferences().launch_sounds);
    b.sync().await;
    assert!(!b.ash.launcher_preferences().launch_sounds);
}

// ---- instances -----------------------------------------------------------------------------

#[tokio::test]
async fn instances_sync_by_their_own_id_not_their_folder_name() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let b = machine(&backend).await;
    // Both machines have a "PvP" in a folder called pvp. They are different instances.
    a.modded("PvP");
    b.ash.create_instance("PvP", "1.8.9", Loader::LegacyFabric).unwrap();
    a.sync().await;
    b.sync().await;
    a.sync().await;

    for m in [&a, &b] {
        let mut versions: Vec<String> = m
            .ash
            .instances()
            .unwrap()
            .into_iter()
            .filter(|i| i.name == "PvP")
            .map(|i| i.version_id)
            .collect();
        versions.sort();
        assert_eq!(versions, ["1.21.11", "1.8.9"], "both, side by side");
    }
}

#[tokio::test]
async fn a_rename_follows_and_a_deletion_is_asked_about_never_done() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    a.sync().await;
    let b = machine(&backend).await;
    b.sync().await;

    a.ash.rename_instance(&on_a.id, "Ranked").unwrap();
    a.sync().await;
    b.sync().await;
    let on_b = b.instance_named("Ranked").expect("renamed on B");

    a.ash.delete_instance(&on_a.id).unwrap();
    a.sync().await;
    let status = b.ash.sync_settings().await;

    assert!(b.instance_named("Ranked").is_some(), "never deleted without asking");
    assert_eq!(status.deleted_elsewhere.len(), 1);
    assert_eq!(status.deleted_elsewhere[0].name, "Ranked");

    // Kept here: it becomes this machine's alone, and isn't sent back.
    b.ash.resolve_deleted_elsewhere(&on_b.id, false).unwrap();
    assert!(b.ash.sync_settings().await.deleted_elsewhere.is_empty());
    a.sync().await;
    assert!(a.instance_named("Ranked").is_none(), "A's deletion stands");
}

#[tokio::test]
async fn deleting_here_after_the_question_removes_it() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    a.sync().await;
    let b = machine(&backend).await;
    b.sync().await;
    a.ash.delete_instance(&on_a.id).unwrap();
    a.sync().await;
    b.sync().await;

    let on_b = b.instance_named("PvP").unwrap();
    b.ash.resolve_deleted_elsewhere(&on_b.id, true).unwrap();

    assert!(b.instance_named("PvP").is_none());
    assert!(b.ash.sync_settings().await.deleted_elsewhere.is_empty());
}

// ---- server lists -----------------------------------------------------------------------------

#[tokio::test]
async fn a_server_list_follows_in_order_and_icons_stay_where_they_are() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    // The game's own list, with Hypixel's icon in it.
    std::fs::copy(
        Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/servers-1.21.11.dat"),
        a.ash.game_directory(&on_a.id).join("servers.dat"),
    )
    .unwrap();
    let original = a.ash.server_list(&on_a.id).unwrap();
    a.sync().await;

    let b = machine(&backend).await;
    b.sync().await;
    let on_b = b.instance_named("PvP").unwrap();
    let addresses = |m: &Machine, i: &Instance| -> Vec<String> {
        m.ash.server_list(&i.id).unwrap().into_iter().map(|s| s.address).collect()
    };
    assert_eq!(
        addresses(&b, &on_b),
        original.iter().map(|s| s.address.clone()).collect::<Vec<_>>()
    );

    // B moves the last server to the top and renames it; A follows.
    let last = original.len() - 1;
    b.ash.move_server(&on_b.id, last, &original[last].address, 0).unwrap();
    b.ash
        .edit_server(&on_b.id, 0, &original[last].address, "Renamed", &original[last].address)
        .unwrap();
    b.sync().await;
    a.sync().await;

    let after = a.ash.server_list(&on_a.id).unwrap();
    assert_eq!(after[0].name, "Renamed");
    assert_eq!(addresses(&a, &on_a), addresses(&b, &on_b));
    for server in &original {
        let now = after.iter().find(|s| s.address == server.address).unwrap();
        assert_eq!(now.icon, server.icon, "{}'s icon, as the game wrote it", server.address);
    }
}

// ---- off, and unreachable ----------------------------------------------------------------------

#[tokio::test]
async fn turned_off_it_sends_nothing_and_changes_nothing() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    a.ash.set_sync_enabled(false).unwrap();
    a.modded("PvP");

    assert_eq!(a.sync().await, SyncState::Off);
    assert!(backend.sent.lock().unwrap().is_empty());
    assert!(backend.settings.lock().unwrap().is_empty());
}

#[tokio::test]
async fn unreachable_is_a_state_and_the_next_sync_catches_up() {
    let backend = Arc::new(Backend::default());
    let a = machine(&backend).await;
    let on_a = a.modded("PvP");
    *backend.down.lock().unwrap() = true;
    a.change_in_game(&on_a, "crosshair.size=9\n");

    assert_eq!(a.sync().await, SyncState::Unreachable);

    *backend.down.lock().unwrap() = false;
    a.sync().await;
    assert_eq!(
        backend.settings.lock().unwrap()["feature:crosshair.size"]["value"],
        "9",
        "sent once ash's servers answered"
    );
}

#[tokio::test]
async fn without_an_ash_session_nothing_is_sent() {
    let backend = Arc::new(Backend::default());
    let tmp = tempfile::tempdir().unwrap();
    let http =
        Wire { backend: Arc::clone(&backend), rest: common::with_auth_routes(FakeHttp::new()) };
    let ash = Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(tmp.path()) },
        Arc::new(http) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    ash.begin_sign_in().await.unwrap();
    ash.poll_sign_in().await.unwrap();

    assert_eq!(ash.sync_settings().await.state, SyncState::NotSignedIn);
    assert!(backend.sent.lock().unwrap().is_empty());
}
