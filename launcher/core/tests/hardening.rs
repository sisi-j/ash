//! Real Windows machines: awkward paths, antivirus, full disks, and two
//! launches at once.
//!
//! Every test here is about a situation nobody chooses. The happy path is
//! covered elsewhere; this file exists for the machines ash will actually be
//! installed on.

use std::path::PathBuf;
use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{Ash, Cancel, Config, InstanceId, NullSink, VERSION_MANIFEST_URL};

mod common;

const VERSION: &str = "1.21.11";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.11.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const LIB_URL: &str = "https://libraries.minecraft.net/com/example/thing/1.0/thing-1.0.jar";
const LIB_PATH: &str = "com/example/thing/1.0/thing-1.0.jar";
const CLIENT_JAR: &[u8] = b"pretend this is the client jar";
const LIB_JAR: &[u8] = b"pretend this is a library";

/// A directory name with a space and a character outside ASCII. Both are
/// ordinary on a real machine - "C:\\Users\\Jos\u{e9} Garc\u{ed}a" is somebody's
/// actual home directory - and both are how a launcher ends up with a
/// classpath the JVM cannot read.
const AWKWARD_ROOT: &str = "Jos\u{e9} Garc\u{ed}a's ash";

fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
        "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},
                      "url":"{CLIENT_URL}"}}}},
        "libraries":[{{"name":"com.example:thing:1.0","downloads":{{"artifact":{{
            "path":"{LIB_PATH}","sha1":"{lib_sha}","size":{lib_size},"url":"{LIB_URL}"}}}}}}],
        "arguments":{{"jvm":["-cp","${{classpath}}",
                            "-Djava.library.path=${{natives_directory}}"],
                      "game":["--username","${{auth_player_name}}",
                              "--accessToken","${{auth_access_token}}",
                              "--gameDir","${{game_directory}}",
                              "--assetsDir","${{assets_root}}"]}}}}"#,
        client_sha = common::sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        lib_sha = common::sha1(LIB_JAR),
        lib_size = LIB_JAR.len(),
    )
}

fn manifest_json() -> String {
    format!(
        r#"{{"latest":{{"release":"{VERSION}","snapshot":"{VERSION}"}},"versions":[
            {{"id":"{VERSION}","type":"release","releaseTime":"2026-01-05T09:00:00+00:00",
              "url":"{VERSION_URL}","sha1":"{}"}}
        ]}}"#,
        common::sha1(version_json().as_bytes())
    )
}

fn serving() -> Arc<FakeHttp> {
    common::with_runtime_routes(common::with_auth_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(VERSION_URL, HttpResponse::ok(version_json()))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR))
            .route(LIB_URL, HttpResponse::ok(LIB_JAR)),
    ))
}

struct Fixture {
    ash: Ash,
    process: Arc<FakeProcessPort>,
    root: PathBuf,
    _tmp: tempfile::TempDir,
}

fn fixture_at(http: Arc<FakeHttp>, folder: &str) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let root = tmp.path().join(folder);
    let process = FakeProcessPort::new();
    let ash = Ash::new(
        Config::rooted_at(&root),
        http as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        Arc::clone(&process) as Arc<dyn ProcessPort>,
        "test-client",
    );
    Fixture { ash, process, root, _tmp: tmp }
}

fn fixture() -> Fixture {
    fixture_at(serving(), "ash")
}

impl Fixture {
    async fn ready(&self, name: &str) -> InstanceId {
        if self.ash.accounts().active_account().is_none() {
            self.ash.begin_sign_in().await.expect("device code");
            self.ash.poll_sign_in().await.expect("sign-in");
        }
        self.ash.create_instance(name, VERSION).expect("instance").id
    }
}

// ---- awkward paths -----------------------------------------------------------

#[tokio::test]
async fn a_path_with_a_space_and_an_accent_reaches_the_jvm_intact() {
    let f = fixture_at(serving(), AWKWARD_ROOT);
    let id = f.ready("modern").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    // Arguments go to the process port as a list, never as one string a
    // shell has to take apart again, so nothing here needs quoting and
    // nothing may be split.
    let spawned = f.process.last();
    let expected = f.ash.game_directory(&id);
    let game_dir = spawned
        .args
        .iter()
        .position(|a| a == "--gameDir")
        .and_then(|at| spawned.args.get(at + 1))
        .expect("--gameDir");

    assert_eq!(PathBuf::from(game_dir), expected);
    assert!(game_dir.contains(AWKWARD_ROOT), "the path was mangled: {game_dir}");
    assert!(game_dir.contains("Jos\u{e9}"), "the accent was lost: {game_dir}");
}

#[tokio::test]
async fn every_classpath_entry_under_an_awkward_path_is_a_file_that_exists() {
    let f = fixture_at(serving(), AWKWARD_ROOT);
    let id = f.ready("modern").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    let spawned = f.process.last();
    let at = spawned.args.iter().position(|a| a == "-cp").expect("-cp");
    let classpath = &spawned.args[at + 1];
    let separator = if cfg!(windows) { ';' } else { ':' };

    // The strongest available statement: not "the string looks right" but
    // "the JVM would find every one of these".
    let entries: Vec<&str> = classpath.split(separator).collect();
    assert_eq!(entries.len(), 2, "expected the library and the client jar");
    for entry in entries {
        assert!(PathBuf::from(entry).is_file(), "{entry} is not a file on disk");
    }
}

#[tokio::test]
async fn an_instance_named_in_another_script_still_gets_a_usable_directory() {
    let f = fixture();
    // The display name keeps the original; the directory does not have to.
    let id = f.ready("\u{30de}\u{30a4}\u{30f3}\u{30af}\u{30e9}").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    let spawned = f.process.last();
    assert!(spawned.working_directory.is_dir());
    assert_eq!(f.ash.instance(&id).expect("instance").name, "\u{30de}\u{30a4}\u{30f3}\u{30af}\u{30e9}");
}

// ---- antivirus ---------------------------------------------------------------

#[tokio::test]
async fn a_file_that_keeps_arriving_altered_names_itself() {
    let f = fixture_at(serving().route(LIB_URL, HttpResponse::ok(b"not what was published".to_vec())), "ash");
    let id = f.ready("modern").await;

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("the bytes do not match the published hash");

    assert_eq!(err.kind(), "verification_failed");
    // Naming the file is the point: it is what a player needs in order to
    // add an exclusion, and "a downloaded file" names nothing.
    let message = err.user_message();
    assert!(message.contains(LIB_PATH), "the message does not name the file: {message}");
    assert!(message.contains("antivirus"), "{message}");
    // It is a path inside ash's own depot, so it leaks nothing of the
    // player's.
    assert!(!message.contains(&f.root.display().to_string()));
}

#[tokio::test]
async fn a_quarantined_file_is_noticed_and_fetched_again() {
    let f = fixture();
    let id = f.ready("modern").await;
    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepare");

    // What quarantine looks like from ash's side: the file was verified, and
    // then it was not there any more.
    let taken = f.root.join(format!("depot/libraries/{LIB_PATH}"));
    assert!(taken.is_file());
    std::fs::remove_file(&taken).expect("remove");

    let plan = f.ash.plan_instance(&id).await.expect("plan");
    assert_eq!(plan.missing_files, 1, "the missing file was not noticed");

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepare again");
    assert!(taken.is_file(), "it was not fetched again");
}

// ---- two at once ---------------------------------------------------------------

#[tokio::test]
async fn a_second_launch_of_the_same_instance_is_refused() {
    let f = fixture();
    let id = f.ready("modern").await;
    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    let err = f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect_err("already running");

    assert_eq!(err.kind(), "already_running");
    assert_eq!(f.process.spawned().len(), 1);
}

#[tokio::test]
async fn a_different_instance_launches_while_the_first_is_running() {
    let f = fixture();
    let first = f.ready("one").await;
    let second = f.ready("two").await;

    f.ash.launch(&first, &NullSink, &Cancel::new()).await.expect("first");
    f.ash.launch(&second, &NullSink, &Cancel::new()).await.expect("second");

    // Two instances is the whole reason instances exist. Refusing the second
    // because the first is up would make the feature pointless.
    assert_eq!(f.process.spawned().len(), 2);
    assert!(matches!(f.ash.game_status(&first), Some(ash_core::GameStatus::Running)));
    assert!(matches!(f.ash.game_status(&second), Some(ash_core::GameStatus::Running)));
}

#[tokio::test]
async fn stopping_one_game_leaves_the_other_running() {
    let f = fixture();
    let first = f.ready("one").await;
    let second = f.ready("two").await;
    f.ash.launch(&first, &NullSink, &Cancel::new()).await.expect("first");
    f.ash.launch(&second, &NullSink, &Cancel::new()).await.expect("second");

    f.ash.stop_game(&first);

    assert!(matches!(f.ash.game_status(&first), Some(ash_core::GameStatus::Exited { .. })));
    assert!(matches!(f.ash.game_status(&second), Some(ash_core::GameStatus::Running)));
}

// ---- the log -------------------------------------------------------------------

#[tokio::test]
async fn ash_writes_the_exact_invocation_it_ran() {
    let f = fixture();
    let id = f.ready("modern").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("a log");
    assert!(log.contains("launch"), "no launch record: {log}");
    assert!(log.contains("net.minecraft.client.main.Main"), "the main class is missing");
    assert!(log.contains("-cp"), "the classpath is missing");
    assert!(log.contains(&format!("instance={id}")));
    // Preparation is the other half of what a player would be asked about.
    assert!(log.contains("prepared"), "preparation was not recorded");
}

#[tokio::test]
async fn no_credential_reaches_the_log() {
    let f = fixture();
    let id = f.ready("modern").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    // The token is on the real command line - the game cannot authenticate
    // without it - so this is the assertion that the logged shape is the
    // redacted one.
    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("a log");
    assert!(log.contains("--accessToken"), "the test proves nothing if the flag is absent");
    assert!(!log.contains(common::MC_TOKEN), "the access token is in the log");
    assert!(!log.contains("eyJ"), "something JWT-shaped is in the log");
    assert!(log.contains("<redacted>"));
}

#[tokio::test]
async fn a_failed_launch_is_recorded_without_the_failure_detail() {
    let f = fixture_at(serving(), "ash");
    f.process.fail_to_spawn("C:/secret/path/java.exe is not executable");
    let id = f.ready("modern").await;

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect_err("spawn failed");

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("a log");
    assert!(log.contains("launch-failed"), "the failure was not recorded");
    assert!(log.contains("launch_failed"), "the kind is what makes it searchable");
    // The kind, not the underlying text. A library's error message is where
    // a path the player never chose to share ends up in a log they paste
    // into a forum.
    assert!(!log.contains("C:/secret/path"), "the raw detail reached the log: {log}");
}

#[tokio::test]
async fn the_log_survives_a_session_and_keeps_appending() {
    let f = fixture();
    let first = f.ready("one").await;
    let second = f.ready("two").await;

    f.ash.launch(&first, &NullSink, &Cancel::new()).await.expect("first");
    f.ash.launch(&second, &NullSink, &Cancel::new()).await.expect("second");

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("a log");
    assert_eq!(log.matches("launch  instance=").count(), 2, "one launch overwrote the other");
}

// ---- a connection that went away ---------------------------------------------

/// The same depot and the same account, with nothing reachable.
fn gone_offline(f: &Fixture) -> Ash {
    Ash::new(
        Config::rooted_at(&f.root),
        FakeHttp::offline() as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    )
}

#[tokio::test]
async fn a_prepared_instance_still_plans_with_no_network() {
    let f = fixture();
    let id = f.ready("modern").await;
    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepare");

    let plan = gone_offline(&f).plan_instance(&id).await.expect("plan from the depot");

    // Planning happens whenever a player looks at an instance. Needing the
    // network for it means a fully downloaded instance shows an error
    // instead of a play button the moment the connection drops.
    assert_eq!(plan.missing_files, 0);
    assert_eq!(plan.version_id, VERSION);
}

#[tokio::test]
async fn a_version_never_downloaded_still_fails_with_no_network() {
    let f = fixture();
    let id = f.ready("modern").await;

    // Nothing was ever fetched for this version, so there is nothing to fall
    // back to and saying so is the only honest answer.
    let err = gone_offline(&f).plan_instance(&id).await.expect_err("nothing cached");

    assert_eq!(err.kind(), "transport");
}

#[tokio::test]
async fn cached_metadata_is_still_held_to_its_published_hash() {
    let f = fixture();
    let id = f.ready("modern").await;
    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepare");

    let cached = f.root.join(format!("depot/versions/{VERSION}/{VERSION}.json"));
    std::fs::write(&cached, r#"{"id":"1.21.11","mainClass":"somebody.Elses.Main"}"#)
        .expect("tamper");

    let err = gone_offline(&f).plan_instance(&id).await.expect_err("the hash does not match");

    // Otherwise "the network is down" becomes the way to get ash to run
    // whatever somebody wrote into its cache.
    assert_eq!(err.kind(), "verification_failed");
}
