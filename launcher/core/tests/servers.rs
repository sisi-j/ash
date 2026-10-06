//! An instance's servers: the game's own list, and asking each how it is.
//!
//! The lists are real `servers.dat` files, each written by its own version's
//! serialiser - see `fixtures/servers-dat.md`. The servers are scripted, on
//! in-memory pipes: `FakeServerPort` refuses any address it has no server
//! for, so nothing here can reach a real one.

use std::sync::Arc;
use std::time::{Duration, Instant};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::FakeHttp;
use ash_core::process::FakeProcessPort;
use ash_core::servers::{FakeServer, FakeServerPort, ServerPort};
use ash_core::{Ash, Config, InstanceId, Loader, ServerEntry, ServerStatus};

const MODERN_LIST: &[u8] = include_bytes!("fixtures/servers-1.21.11.dat");
const LEGACY_LIST: &[u8] = include_bytes!("fixtures/servers-1.8.9.dat");

/// The 1x1 PNG both fixtures give Hypixel.
const ICON: &str = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

const TIMEOUT: Duration = Duration::from_millis(300);

struct Fixture {
    ash: Ash,
    servers: Arc<FakeServerPort>,
    _tmp: tempfile::TempDir,
}

fn fixture() -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let servers = FakeServerPort::new();
    let ash = Ash::new(
        Config { server_timeout: TIMEOUT, ..Config::rooted_at(tmp.path()) },
        FakeHttp::new(),
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        Arc::clone(&servers) as Arc<dyn ServerPort>,
        "test-client",
    );
    Fixture { ash, servers, _tmp: tmp }
}

impl Fixture {
    /// An instance on `version` whose game has written `list`.
    fn instance(&self, version: &str, list: &[u8]) -> InstanceId {
        let id = self.ash.create_instance(version, version, Loader::Vanilla).expect("instance").id;
        std::fs::write(self.ash.game_directory(&id).join("servers.dat"), list)
            .expect("servers.dat");
        id
    }
}

fn online(players: i64) -> FakeServer {
    FakeServer::Online {
        status: format!(
            r#"{{"version":{{"name":"Paper 1.21.11","protocol":774}},
                "players":{{"max":100,"online":{players}}},
                "description":{{"text":"A §btest§r server"}},
                "favicon":"data:image/png;base64,{ICON}"}}"#
        ),
    }
}

// ---- the list ----------------------------------------------------------------

#[test]
fn a_list_written_by_1_21_11_reads_in_its_order_without_its_hidden_entry() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);

    let servers = f.ash.servers(&id).unwrap();

    assert_eq!(
        servers,
        [
            ServerEntry {
                name: "Hypixel".into(),
                address: "mc.hypixel.net".into(),
                icon: Some(ICON.into()),
                last_joined_ms: None,
            },
            ServerEntry {
                name: "Local test".into(),
                address: "localhost:25570".into(),
                icon: None,
                last_joined_ms: None,
            },
            ServerEntry {
                name: "Spëcial ★ server".into(),
                address: "[::1]:25565".into(),
                icon: None,
                last_joined_ms: None,
            },
        ]
    );
}

#[test]
fn a_list_written_by_1_8_9_reads_the_same_way() {
    let f = fixture();
    let id = f.instance("1.8.9", LEGACY_LIST);

    let servers = f.ash.servers(&id).unwrap();

    let names: Vec<_> = servers.iter().map(|s| s.name.as_str()).collect();
    assert_eq!(names, ["Hypixel", "Local test", "Spëcial ★ server"]);
    assert_eq!(servers[0].icon.as_deref(), Some(ICON));
    assert_eq!(servers[1].address, "localhost:25570");
}

#[test]
fn a_game_that_never_opened_its_server_list_has_no_servers() {
    let f = fixture();
    let id = f.ash.create_instance("fresh", "1.21.11", Loader::Vanilla).unwrap().id;

    assert!(f.ash.servers(&id).unwrap().is_empty());
}

#[test]
fn a_list_ash_cannot_read_shows_as_none_and_says_so_in_the_log() {
    let f = fixture();
    let id = f.instance("1.21.11", &MODERN_LIST[..MODERN_LIST.len() / 2]);

    assert!(f.ash.servers(&id).unwrap().is_empty());

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    assert!(log.contains("servers-unreadable"), "{log}");
}

// ---- asking a server ---------------------------------------------------------

#[tokio::test]
async fn an_online_server_answers_with_its_players_in_the_instances_own_protocol() {
    let f = fixture();
    f.servers.serve("localhost", 25570, online(42));
    let modern = f.instance("1.21.11", MODERN_LIST);
    let legacy = f.instance("1.8.9", LEGACY_LIST);

    let status = f.ash.server_status(&modern, "localhost:25570").await.unwrap();
    f.ash.server_status(&legacy, "localhost:25570").await.unwrap();

    assert_eq!(
        status,
        ServerStatus::Online {
            players_online: 42,
            players_max: 100,
            version: "Paper 1.21.11".into(),
            motd: "A test server".into(),
            icon: Some(ICON.into()),
        }
    );
    let handshakes = f.servers.handshakes();
    let protocols: Vec<_> = handshakes.iter().map(|h| h.protocol).collect();
    assert_eq!(protocols, [774, 47], "each instance's handshake names its own version");
    assert_eq!(
        (handshakes[0].host.as_str(), handshakes[0].port, handshakes[0].next_state),
        ("localhost", 25570, 1)
    );
}

#[tokio::test]
async fn a_server_nothing_answers_for_is_offline() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);

    let status = f.ash.server_status(&id, "localhost:25570").await.unwrap();

    assert_eq!(status, ServerStatus::Offline);
    assert_eq!(f.servers.connections(), [("localhost".to_owned(), 25570)]);
}

#[tokio::test]
async fn a_server_that_never_answers_is_offline_once_the_timeout_passes() {
    let f = fixture();
    f.servers.serve("localhost", 25570, FakeServer::Silent);
    let id = f.instance("1.21.11", MODERN_LIST);

    let started = Instant::now();
    let status = f.ash.server_status(&id, "localhost:25570").await.unwrap();

    assert_eq!(status, ServerStatus::Offline);
    let waited = started.elapsed();
    assert!(waited >= TIMEOUT && waited < TIMEOUT * 5, "waited {waited:?}");
}

#[tokio::test]
async fn an_address_with_no_port_follows_its_srv_record() {
    let f = fixture();
    f.servers.redirect("mc.hypixel.net", "proxy.hypixel.example", 25599).serve(
        "proxy.hypixel.example",
        25599,
        online(70_000),
    );
    let id = f.instance("1.21.11", MODERN_LIST);

    let status = f.ash.server_status(&id, "mc.hypixel.net").await.unwrap();

    assert!(matches!(status, ServerStatus::Online { players_online: 70_000, .. }), "{status:?}");
    assert_eq!(f.servers.connections(), [("proxy.hypixel.example".to_owned(), 25599)]);
    // The game names the server it reached, not the one it was redirected from.
    assert_eq!(f.servers.handshakes()[0].host, "proxy.hypixel.example");
}

#[tokio::test]
async fn an_address_with_a_port_is_not_redirected() {
    let f = fixture();
    // A record the game would not consult for an address with a port.
    f.servers.redirect("localhost", "elsewhere.example", 1).serve("localhost", 25570, online(1));
    let id = f.instance("1.21.11", MODERN_LIST);

    f.ash.server_status(&id, "localhost:25570").await.unwrap();

    assert_eq!(f.servers.connections(), [("localhost".to_owned(), 25570)]);
}

#[tokio::test]
async fn a_server_is_asked_at_most_once_a_minute() {
    let f = fixture();
    f.servers.serve("localhost", 25570, online(5));
    let id = f.instance("1.21.11", MODERN_LIST);

    let first = f.ash.server_status(&id, "localhost:25570").await.unwrap();
    let second = f.ash.server_status(&id, "localhost:25570").await.unwrap();

    assert_eq!(first, second);
    assert_eq!(f.servers.connections().len(), 1, "asked twice inside a minute");
}

#[tokio::test]
async fn a_server_the_player_has_not_listed_is_never_asked() {
    let f = fixture();
    f.servers.serve("unlisted.example", 25565, online(1));
    let id = f.instance("1.21.11", MODERN_LIST);

    let err = f.ash.server_status(&id, "unlisted.example").await.expect_err("not listed");

    assert_eq!(err.kind(), "server_not_listed");
    assert!(f.servers.connections().is_empty(), "an unlisted server was contacted");
}

// ---- recent servers -----------------------------------------------------------
//
// ash's client records every join, with when, in `ash/recent-servers.json`;
// the card puts the most recent first. The record is the client's: the
// launcher only reads it.

/// The record as the client writes it: the contract both sides test against.
const RECENT: &str = include_str!("fixtures/recent-servers.json");

impl Fixture {
    fn joined(&self, id: &InstanceId, record: &str) {
        let path = self.ash.game_directory(id).join("ash").join("recent-servers.json");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(path, record).unwrap();
    }
}

fn addresses(servers: &[ServerEntry]) -> Vec<&str> {
    servers.iter().map(|s| s.address.as_str()).collect()
}

#[test]
fn the_most_recently_joined_come_first_then_the_rest_of_the_list_in_its_order() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, RECENT);

    let servers = f.ash.servers(&id).unwrap();

    assert_eq!(addresses(&servers), ["mc.hypixel.net", "localhost:25570", "[::1]:25565"]);
    assert_eq!(servers[0].last_joined_ms, Some(1_791_300_000_000));
    assert_eq!(servers[0].name, "Hypixel", "a listed server lost its own name and icon");
    assert_eq!(servers[2].last_joined_ms, None);
}

#[test]
fn a_server_joined_by_direct_connect_is_there_by_its_address() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, r#"{ "servers": [ { "address": "Play.MCCIsland.net", "joined_ms": 5 } ] }"#);

    let servers = f.ash.servers(&id).unwrap();

    assert_eq!(servers[0].name, "Play.MCCIsland.net");
    assert_eq!(servers[0].icon, None);
    assert_eq!(servers.len(), 4, "the list lost a server");
}

#[test]
fn an_address_written_differently_is_still_the_listed_server() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, r#"{ "servers": [ { "address": " MC.Hypixel.NET. ", "joined_ms": 5 } ] }"#);

    let servers = f.ash.servers(&id).unwrap();

    assert_eq!(addresses(&servers), ["mc.hypixel.net", "localhost:25570", "[::1]:25565"]);
    assert_eq!(servers[0].last_joined_ms, Some(5));
}

#[test]
fn an_entry_that_is_not_an_address_is_dropped_and_the_rest_stand() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(
        &id,
        r#"{ "servers": [ { "address": "host:notaport", "joined_ms": 9 },
                          { "address": "localhost:25570", "joined_ms": 3 } ] }"#,
    );

    let servers = f.ash.servers(&id).unwrap();

    assert_eq!(addresses(&servers)[0], "localhost:25570");
    assert!(!addresses(&servers).contains(&"host:notaport"));
}

#[test]
fn a_record_ash_cannot_read_leaves_the_list_as_the_game_has_it_and_is_logged() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, "not json at all");

    assert_eq!(
        addresses(&f.ash.servers(&id).unwrap()),
        ["mc.hypixel.net", "localhost:25570", "[::1]:25565"]
    );
    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    assert!(log.contains("recent-servers-unreadable"), "{log}");
    assert!(!log.contains("not json"), "the file's text reached the log");
}

#[test]
fn the_launcher_never_writes_the_record() {
    let f = fixture();
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, RECENT);
    let path = f.ash.game_directory(&id).join("ash").join("recent-servers.json");

    f.ash.servers(&id).unwrap();

    assert_eq!(std::fs::read_to_string(path).unwrap(), RECENT);
}

#[tokio::test]
async fn a_server_the_player_joined_but_never_listed_can_be_asked() {
    let f = fixture();
    f.servers.serve("play.mccisland.net", 25565, online(236));
    let id = f.instance("1.21.11", MODERN_LIST);
    f.joined(&id, r#"{ "servers": [ { "address": "play.mccisland.net", "joined_ms": 5 } ] }"#);

    let status = f.ash.server_status(&id, "play.mccisland.net").await.unwrap();

    assert!(matches!(status, ServerStatus::Online { players_online: 236, .. }), "{status:?}");
}
