//! Launching 1.21.x.
//!
//! The assertion is the **recorded invocation**: the exact command ash would
//! run, captured by the process port's fake. No JVM starts here, and none
//! should - a test that opened a game window would be a test nobody could
//! run twice.
//!
//! The metadata fixture is modelled on a real 1.21.x manifest, including the
//! two things that shape are easy to get wrong: every conditional game
//! argument is feature-gated, and the three Windows native jars carry one
//! identical rule.

use std::sync::Arc;

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, GameStatus, ProcessPort};
use ash_core::{Ash, Cancel, Config, InstanceId, NullSink, VERSION_MANIFEST_URL};

mod common;

const VERSION: &str = "1.21.11";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.11.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const ASSET_INDEX_URL: &str = "https://piston-meta.mojang.com/v1/packages/dd/29.json";
const LIB_BASE: &str = "https://libraries.minecraft.net";

const CLIENT_JAR: &[u8] = b"pretend this is the client jar";
const ANY_JAR: &[u8] = b"pretend this is a library jar";
const ASSET: &[u8] = b"pretend this is an asset";

/// Every library the fixture publishes: Maven name, and the relative path
/// Mojang would give it.
///
/// The three Windows natives all carry `{"os": {"name": "windows"}}` - the
/// architecture appears only in the classifier - which is exactly the shape
/// that makes rule evaluation alone insufficient.
const LIBRARIES: &[(&str, &str, Option<&str>)] = &[
    ("com.mojang:blocklist:1.0.10", "com/mojang/blocklist/1.0.10/blocklist-1.0.10.jar", None),
    ("org.lwjgl:lwjgl:3.3.3", "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", None),
    (
        "org.lwjgl:lwjgl:3.3.3:natives-windows",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
        Some("windows"),
    ),
    (
        "org.lwjgl:lwjgl:3.3.3:natives-windows-x86",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-x86.jar",
        Some("windows"),
    ),
    (
        "org.lwjgl:lwjgl:3.3.3:natives-windows-arm64",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-arm64.jar",
        Some("windows"),
    ),
    (
        "org.lwjgl:lwjgl:3.3.3:natives-macos",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos.jar",
        Some("osx"),
    ),
    (
        "org.lwjgl:lwjgl:3.3.3:natives-macos-arm64",
        "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-macos-arm64.jar",
        Some("osx"),
    ),
];

fn libraries_json() -> String {
    let entries: Vec<String> = LIBRARIES
        .iter()
        .map(|(name, path, os)| {
            let rules = match os {
                Some(os) => {
                    format!(r#","rules":[{{"action":"allow","os":{{"name":"{os}"}}}}]"#)
                }
                None => String::new(),
            };
            format!(
                r#"{{"name":"{name}","downloads":{{"artifact":{{"path":"{path}",
                   "sha1":"{sha}","size":{size},"url":"{LIB_BASE}/{path}"}}}}{rules}}}"#,
                sha = common::sha1(ANY_JAR),
                size = ANY_JAR.len(),
            )
        })
        .collect();
    entries.join(",")
}

/// A 1.21.x manifest, argument-for-argument.
fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
        "assetIndex":{{"id":"29","sha1":"{index_sha}","size":{index_size},
                      "url":"{ASSET_INDEX_URL}"}},
        "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},
                      "url":"{CLIENT_URL}"}}}},
        "libraries":[{libraries}],
        "arguments":{{
          "jvm":[
            {{"rules":[{{"action":"allow","os":{{"name":"osx"}}}}],
             "value":["-XstartOnFirstThread"]}},
            {{"rules":[{{"action":"allow","os":{{"name":"windows"}}}}],
             "value":"-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump"}},
            {{"rules":[{{"action":"allow","os":{{"arch":"x86"}}}}],"value":"-Xss1M"}},
            "-Djava.library.path=${{natives_directory}}",
            "-Dminecraft.launcher.brand=${{launcher_name}}",
            "-Dminecraft.launcher.version=${{launcher_version}}",
            "-cp","${{classpath}}"
          ],
          "game":[
            "--username","${{auth_player_name}}",
            "--version","${{version_name}}",
            "--gameDir","${{game_directory}}",
            "--assetsDir","${{assets_root}}",
            "--assetIndex","${{assets_index_name}}",
            "--uuid","${{auth_uuid}}",
            "--accessToken","${{auth_access_token}}",
            "--clientId","${{clientid}}",
            "--xuid","${{auth_xuid}}",
            "--versionType","${{version_type}}",
            {{"rules":[{{"action":"allow","features":{{"is_demo_user":true}}}}],
             "value":"--demo"}},
            {{"rules":[{{"action":"allow","features":{{"has_custom_resolution":true}}}}],
             "value":["--width","${{resolution_width}}","--height","${{resolution_height}}"]}},
            {{"rules":[{{"action":"allow","features":{{"has_quick_plays_support":true}}}}],
             "value":["--quickPlayPath","${{quickPlayPath}}"]}}
          ]
        }}}}"#,
        index_sha = common::sha1(asset_index_json().as_bytes()),
        index_size = asset_index_json().len(),
        client_sha = common::sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        libraries = libraries_json(),
    )
}

fn asset_index_json() -> String {
    format!(
        r#"{{"objects":{{"icons/icon_16x16.png":{{"hash":"{}","size":{}}}}}}}"#,
        common::sha1(ASSET),
        ASSET.len()
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

fn asset_url() -> String {
    let hash = common::sha1(ASSET);
    format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash)
}

fn serving() -> Arc<FakeHttp> {
    let mut http = common::with_runtime_routes(common::with_auth_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(VERSION_URL, HttpResponse::ok(version_json()))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR))
            .route(ASSET_INDEX_URL, HttpResponse::ok(asset_index_json()))
            .route(asset_url(), HttpResponse::ok(ASSET)),
    ));
    for (_, path, _) in LIBRARIES {
        http = http.route(format!("{LIB_BASE}/{path}"), HttpResponse::ok(ANY_JAR));
    }
    http
}

struct Fixture {
    ash: Ash,
    process: Arc<FakeProcessPort>,
    store: Arc<InMemoryCredentialStore>,
    tmp: tempfile::TempDir,
}

fn fixture_with(process: Arc<FakeProcessPort>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let store = InMemoryCredentialStore::new();
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        serving() as Arc<dyn HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        Arc::clone(&process) as Arc<dyn ProcessPort>,
        "test-client",
    );
    Fixture { ash, process, store, tmp }
}

fn fixture() -> Fixture {
    fixture_with(FakeProcessPort::new())
}

impl Fixture {
    /// An instance with a signed-in player behind it.
    async fn ready(&self) -> InstanceId {
        self.ash.begin_sign_in().await.expect("device code");
        self.ash.poll_sign_in().await.expect("sign-in");
        self.ash.create_instance("modern", VERSION).expect("instance").id
    }

    async fn launch(&self, id: &InstanceId) -> ash_core::InvocationView {
        self.ash.launch(id, &NullSink, &Cancel::new()).await.expect("launch")
    }
}

/// The arguments after `--flag`, so a test can ask what was passed for one.
fn value_of<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| a == flag)?;
    args.get(at + 1).map(String::as_str)
}

// ---- the command ash builds -------------------------------------------------

#[tokio::test]
async fn the_game_runs_on_the_depots_own_java() {
    let f = fixture();
    let id = f.ready().await;

    f.launch(&id).await;

    let spawned = f.process.last();
    assert!(
        spawned.program.starts_with(f.tmp.path().join("depot")),
        "{} is not the runtime ash provisioned",
        spawned.program.display()
    );
}

#[tokio::test]
async fn the_main_class_sits_between_the_jvm_and_game_arguments() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;

    let main_at = view
        .args
        .iter()
        .position(|a| a == "net.minecraft.client.main.Main")
        .expect("the main class is on the command line");
    let classpath_at = view.args.iter().position(|a| a == "-cp").expect("-cp");
    let username_at = view.args.iter().position(|a| a == "--username").expect("--username");

    // JVM arguments before the class, game arguments after. Any other order
    // and the JVM reads them as arguments to the program.
    assert!(classpath_at < main_at, "-cp must precede the main class");
    assert!(main_at < username_at, "game arguments must follow the main class");
}

#[tokio::test]
async fn the_classpath_ends_with_the_client_jar() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;
    let classpath = value_of(&view.args, "-cp").expect("a classpath");
    let entries: Vec<&str> = classpath.split(if cfg!(windows) { ';' } else { ':' }).collect();

    // Last, so no library can shadow a game class. This is the order
    // Mojang's own launcher uses.
    assert!(
        entries.last().expect("entries").ends_with(&format!("{VERSION}.jar")),
        "the client jar must come last, got {:?}",
        entries.last()
    );
    assert!(entries.iter().any(|e| e.ends_with("blocklist-1.0.10.jar")), "libraries are missing");
}

#[tokio::test]
async fn only_this_platforms_native_jar_reaches_the_classpath() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;
    let classpath = value_of(&view.args, "-cp").expect("a classpath");

    // All three Windows natives carry the same rule, so rule evaluation
    // alone keeps every one - which would put 32-bit natives beside 64-bit
    // ones and let the JVM pick.
    let natives: Vec<&str> = classpath
        .split(if cfg!(windows) { ';' } else { ':' })
        .filter(|entry| entry.contains("natives"))
        .collect();

    assert_eq!(natives.len(), 1, "expected exactly one native jar, got {natives:?}");
    let expected = if cfg!(windows) {
        if cfg!(target_arch = "aarch64") {
            "natives-windows-arm64.jar"
        } else {
            "natives-windows.jar"
        }
    } else if cfg!(target_arch = "aarch64") {
        "natives-macos-arm64.jar"
    } else {
        "natives-macos.jar"
    };
    assert!(natives[0].ends_with(expected), "{} is the wrong architecture", natives[0]);
}

#[tokio::test]
async fn feature_gated_arguments_never_reach_the_command_line() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;

    // ash turns none of these on. Every one of them is guarded only by a
    // `features` clause, so dropping that clause launches the game in demo
    // mode at a resolution nobody asked for.
    for unwanted in ["--demo", "--width", "--height", "--quickPlayPath"] {
        assert!(!view.args.iter().any(|a| a == unwanted), "{unwanted} should not be passed");
    }
}

#[tokio::test]
async fn nothing_reaches_the_command_line_with_a_placeholder_left_in_it() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;

    // The canary for a version that introduces a placeholder ash does not
    // know: assembly leaves it visible rather than passing an empty string,
    // so it fails here instead of confusing the game.
    let unresolved: Vec<&String> = view.args.iter().filter(|a| a.contains("${")).collect();
    assert!(unresolved.is_empty(), "unsubstituted placeholders: {unresolved:?}");
}

#[tokio::test]
async fn the_player_is_identified_by_the_signed_in_account() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;

    assert_eq!(value_of(&view.args, "--username"), Some(common::PLAYER_NAME));
    assert_eq!(value_of(&view.args, "--uuid"), Some(common::PLAYER_UUID));
    assert_eq!(value_of(&view.args, "--xuid"), Some(common::PLAYER_XUID));
    assert_eq!(value_of(&view.args, "--versionType"), Some("release"));
}

#[tokio::test]
async fn the_game_runs_in_the_instances_own_directory() {
    let f = fixture();
    let id = f.ready().await;

    f.launch(&id).await;

    // ADR-0008: isolated instances, shared depot. Worlds and screenshots
    // belong to the instance; the files the game reads come from the depot.
    let spawned = f.process.last();
    assert_eq!(spawned.working_directory, f.ash.game_directory(&id));
    assert_eq!(
        value_of(&spawned.args, "--gameDir").map(std::path::PathBuf::from),
        Some(f.ash.game_directory(&id))
    );
    assert_eq!(
        value_of(&spawned.args, "--assetsDir").map(std::path::PathBuf::from),
        Some(f.tmp.path().join("depot/assets"))
    );
}

// ---- the access token -------------------------------------------------------

#[tokio::test]
async fn the_game_gets_the_access_token_and_nobody_else_does() {
    let f = fixture();
    let id = f.ready().await;

    let view = f.launch(&id).await;

    // The real command carries it, because the game cannot authenticate
    // without it.
    let spawned = f.process.last();
    assert_eq!(value_of(&spawned.args, "--accessToken"), Some(common::MC_TOKEN));

    // The view is what crosses to the UI, and it must not.
    assert_eq!(value_of(&view.args, "--accessToken"), Some("<redacted>"));
    assert!(
        !view.args.iter().any(|a| a.contains(common::MC_TOKEN)),
        "the access token leaked into the view"
    );
}

#[tokio::test]
async fn the_access_token_is_never_written_to_disk() {
    let f = fixture();
    let id = f.ready().await;
    f.launch(&id).await;

    // ADR-0007: secrets live in the OS credential store, never in ash's own
    // files. The Minecraft token is shorter-lived still - it exists only for
    // as long as it takes to build a command line.
    let mut checked = 0;
    for entry in walk(f.tmp.path()) {
        let Ok(bytes) = std::fs::read(&entry) else {
            continue;
        };
        checked += 1;
        assert!(
            !String::from_utf8_lossy(&bytes).contains(common::MC_TOKEN),
            "{} contains the access token",
            entry.display()
        );
    }
    assert!(checked > 0, "the walk found nothing, so it proved nothing");
}

fn walk(root: &std::path::Path) -> Vec<std::path::PathBuf> {
    let mut found = Vec::new();
    let mut stack = vec![root.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else {
                found.push(path);
            }
        }
    }
    found
}

// ---- what has to be true before anything spawns -----------------------------

#[tokio::test]
async fn launching_without_an_account_spawns_nothing() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", VERSION).expect("instance");

    let err = f
        .ash
        .launch(&instance.id, &NullSink, &Cancel::new())
        .await
        .expect_err("nobody is signed in");

    assert_eq!(err.kind(), "no_account_selected");
    assert!(f.process.spawned().is_empty(), "a JVM was started without a session");
}

#[tokio::test]
async fn a_session_microsoft_no_longer_accepts_stops_the_launch() {
    let f = fixture();
    let id = f.ready().await;

    // Same account, same stored refresh token - but Microsoft now rejects
    // it. That is what a sign-in going stale looks like from here, and no
    // amount of preparing fixes it.
    let stale = Ash::new(
        Config::rooted_at(f.tmp.path()),
        serving().route(common::TOKEN_URL, HttpResponse::json(400, r#"{"error":"invalid_grant"}"#))
            as Arc<dyn HttpPort>,
        Arc::clone(&f.store) as Arc<dyn CredentialStore>,
        Arc::clone(&f.process) as Arc<dyn ProcessPort>,
        "test-client",
    );

    let err = stale.launch(&id, &NullSink, &Cancel::new()).await.expect_err("stale session");

    assert_eq!(err.kind(), "session_expired");
    assert!(err.is_retryable(), "signing in again is exactly the fix");
    assert!(f.process.spawned().is_empty(), "a JVM was started without a session");
}

#[tokio::test]
async fn launching_prepares_whatever_is_missing_first() {
    let f = fixture();
    let id = f.ready().await;

    f.launch(&id).await;

    // The command line names these files; launching without fetching them
    // would produce a classpath pointing at nothing.
    let depot = f.tmp.path().join("depot");
    assert!(depot.join(format!("versions/{VERSION}/{VERSION}.jar")).is_file(), "the client jar");
    assert!(
        depot.join("libraries/com/mojang/blocklist/1.0.10/blocklist-1.0.10.jar").is_file(),
        "a library"
    );
}

#[tokio::test]
async fn a_preview_builds_the_same_command_and_starts_nothing() {
    let f = fixture();
    let id = f.ready().await;

    let previewed = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("preview");
    assert!(f.process.spawned().is_empty(), "previewing started the game");

    let launched = f.launch(&id).await;
    assert_eq!(previewed, launched, "the preview did not describe the launch");
}

#[tokio::test]
async fn launching_a_running_instance_again_is_refused() {
    let f = fixture();
    let id = f.ready().await;
    f.launch(&id).await;

    let err = f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect_err("already running");

    assert_eq!(err.kind(), "already_running");
    assert_eq!(f.process.spawned().len(), 1, "a second JVM was started");
}

#[tokio::test]
async fn a_game_that_has_exited_can_be_launched_again() {
    let f = fixture_with(
        FakeProcessPort::new().set_status(GameStatus::Exited { code: Some(0), clean: true }),
    );
    let id = f.ready().await;
    f.launch(&id).await;

    f.launch(&id).await;

    assert_eq!(f.process.spawned().len(), 2);
}

#[tokio::test]
async fn a_failed_spawn_leaves_nothing_recorded_as_running() {
    let f = fixture_with(FakeProcessPort::new().fail_to_spawn("the runtime is not executable"));
    let id = f.ready().await;

    let err = f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect_err("spawn failed");

    assert_eq!(err.kind(), "launch_failed");
    assert!(err.is_retryable(), "a damaged runtime is worth trying again after preparing");
    assert_eq!(f.ash.game_status(&id), None, "a game that never started is not running");
    assert!(!err.user_message().contains("not executable"), "leaked the underlying detail");
}

// ---- while it runs, and after ----------------------------------------------

#[tokio::test]
async fn an_instance_that_was_never_launched_has_no_status() {
    let f = fixture();
    let id = f.ready().await;

    assert_eq!(f.ash.game_status(&id), None);
    assert!(f.ash.game_log(&id).is_empty());
}

#[tokio::test]
async fn a_clean_exit_and_a_crash_are_told_apart() {
    let clean = fixture_with(
        FakeProcessPort::new().set_status(GameStatus::Exited { code: Some(0), clean: true }),
    );
    let id = clean.ready().await;
    clean.launch(&id).await;
    assert_eq!(clean.ash.game_status(&id), Some(GameStatus::Exited { code: Some(0), clean: true }));

    let crashed = fixture_with(
        FakeProcessPort::new().set_status(GameStatus::Exited { code: Some(1), clean: false }),
    );
    let id = crashed.ready().await;
    crashed.launch(&id).await;

    // Showing a player "finished" for both is how a crash goes unnoticed.
    assert_eq!(
        crashed.ash.game_status(&id),
        Some(GameStatus::Exited { code: Some(1), clean: false })
    );
}

#[tokio::test]
async fn the_log_survives_the_crash_that_produced_it() {
    let f = fixture_with(
        FakeProcessPort::new()
            .set_status(GameStatus::Exited { code: Some(1), clean: false })
            .set_log(&["[main/INFO]: Setting user: oogz", "java.lang.OutOfMemoryError"]),
    );
    let id = f.ready().await;
    f.launch(&id).await;

    // Reading it is the only way to find out why, and it is only worth
    // reading once the game has already gone.
    let log = f.ash.game_log(&id);
    assert_eq!(log.len(), 2);
    assert!(log[1].contains("OutOfMemoryError"));
}

#[tokio::test]
async fn ash_keeps_running_while_the_game_does() {
    let f = fixture();
    let id = f.ready().await;

    f.launch(&id).await;

    // Launching returned with the game still up, and the seam still answers.
    assert_eq!(f.ash.game_status(&id), Some(GameStatus::Running));
    assert_eq!(f.ash.instances().expect("instances").len(), 1);
    assert!(f.ash.catalogue().await.is_ok());
}

#[tokio::test]
async fn stopping_a_game_ends_it() {
    let f = fixture();
    let id = f.ready().await;
    f.launch(&id).await;

    f.ash.stop_game(&id);

    assert!(matches!(f.ash.game_status(&id), Some(GameStatus::Exited { .. })));
}

#[tokio::test]
async fn launching_records_that_the_instance_was_played() {
    let f = fixture();
    let id = f.ready().await;
    assert_eq!(f.ash.instance(&id).expect("instance").last_played_ms, None);

    f.launch(&id).await;

    // Recorded on launch, not on exit: a session that ends in a crash still
    // happened.
    assert!(f.ash.instance(&id).expect("instance").last_played_ms.is_some());
}
