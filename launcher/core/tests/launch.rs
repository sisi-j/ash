//! Launching, on both version targets.
//!
//! The assertion is the **recorded invocation**: the exact command ash would
//! run, captured by the process port's fake. No JVM starts here, and none
//! should - a test that opened a game window would be a test nobody could
//! run twice.
//!
//! Both fixtures are modelled on real manifests, including the things about
//! their shape that are easy to get wrong. 1.21.x: every conditional game
//! argument is feature-gated, and the three Windows native jars carry one
//! identical rule. 1.8.9: the arguments are a single string, the natives are
//! inside jars that have to be unpacked, and one classifier is templated
//! with `${arch}`.

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

const LEGACY: &str = "1.8.9";
const LEGACY_URL: &str = "https://piston-meta.mojang.com/v1/packages/ee/1.8.9.json";
const LEGACY_CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/ff/client-1.8.9.jar";
const LEGACY_INDEX_URL: &str = "https://launchermeta.mojang.com/v1/packages/gg/1.8.json";
const LOG_CONFIG_URL: &str = "https://launcher.mojang.com/v1/objects/hh/client-1.7.xml";

/// Mojang's patched log4j configuration. The `RegexFilter` denying any
/// message containing a `${...}` lookup *is* the Log4Shell mitigation for
/// this era - these versions ship a log4j too old for
/// `formatMsgNoLookups`, so the config is the whole fix.
const LOG_CONFIG: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN"><Appenders><Console name="SysOut" target="SYSTEM_OUT">
<XMLLayout /></Console></Appenders><Loggers><Root level="info"><filters>
<RegexFilter regex="(?s).*\$\{[^}]*\}.*" onMatch="DENY" onMismatch="NEUTRAL"/>
</filters><AppenderRef ref="SysOut"/></Root></Loggers></Configuration>"#;

/// The library inside a native jar, and the manifest entry that must not be
/// unpacked beside it.
const NATIVE_FILE: &str = "lwjgl64.dll";
const NATIVE_BODY: &[u8] = b"pretend this is a native library";
const MANIFEST_ENTRY: &str = "META-INF/MANIFEST.MF";

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

// ---- 1.8.9 -----------------------------------------------------------------

/// A native jar: the library, plus the manifest `extract.exclude` says to
/// leave behind.
fn native_jar() -> Vec<u8> {
    use std::io::Write;
    use zip::write::SimpleFileOptions;

    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    // Stored, not deflated: ash only ever reads jars, so its zip support is
    // decompress-only and a fixture it cannot read would prove nothing.
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);

    writer.start_file(MANIFEST_ENTRY, options).expect("manifest entry");
    writer
        .write_all(
            b"Manifest-Version: 1.0
",
        )
        .expect("manifest body");
    writer.start_file(NATIVE_FILE, options).expect("library entry");
    writer.write_all(NATIVE_BODY).expect("library body");

    writer.finish().expect("finish").into_inner()
}

/// Native libraries as 1.8.9 declares them: a `natives` map pointing into
/// `downloads.classifiers`, not a classifier on the Maven coordinate.
fn legacy_libraries() -> String {
    let jar = native_jar();
    let classifier = |name: &str| {
        format!(
            r#""{name}":{{"path":"legacy/{name}.jar","sha1":"{}","size":{},
               "url":"{LIB_BASE}/legacy/{name}.jar"}}"#,
            common::sha1(&jar),
            jar.len()
        )
    };

    format!(
        r#"{{"name":"org.lwjgl.lwjgl:lwjgl-platform:2.9.4",
            "downloads":{{"artifact":{{"path":"legacy/lwjgl-platform.jar","sha1":"{stub_sha}",
              "size":{stub_size},"url":"{LIB_BASE}/legacy/lwjgl-platform.jar"}},
              "classifiers":{{{windows},{osx}}}}},
            "natives":{{"windows":"natives-windows","osx":"natives-osx"}},
            "extract":{{"exclude":["META-INF/"]}}}},
          {{"name":"tv.twitch:twitch-platform:6.5",
            "downloads":{{"classifiers":{{{arch_windows},{osx}}}}},
            "natives":{{"windows":"natives-windows-${{arch}}","osx":"natives-osx"}},
            "extract":{{"exclude":["META-INF/"]}}}},
          {{"name":"net.java.jinput:jinput:2.0.5",
            "downloads":{{"artifact":{{"path":"legacy/jinput.jar","sha1":"{stub_sha}",
              "size":{stub_size},"url":"{LIB_BASE}/legacy/jinput.jar"}}}}}}"#,
        stub_sha = common::sha1(ANY_JAR),
        stub_size = ANY_JAR.len(),
        windows = classifier("natives-windows"),
        osx = classifier("natives-osx"),
        // `${arch}` is templated by the launcher, never by Mojang.
        arch_windows = classifier("natives-windows-64"),
    )
}

fn legacy_index_json() -> String {
    format!(
        r#"{{"objects":{{"legacy.png":{{"hash":"{}","size":{}}}}}}}"#,
        common::sha1(ASSET),
        ASSET.len()
    )
}

/// 1.8.9: one argument string, a `jre-legacy` runtime, and the log4j
/// configuration that carries the Log4Shell mitigation.
fn legacy_json() -> String {
    format!(
        r#"{{"id":"{LEGACY}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"jre-legacy","majorVersion":8}},
        "assetIndex":{{"id":"1.8","sha1":"{index_sha}","size":{index_size},
                      "url":"{LEGACY_INDEX_URL}"}},
        "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},
                      "url":"{LEGACY_CLIENT_URL}"}}}},
        "libraries":[{libraries}],
        "logging":{{"client":{{"argument":"-Dlog4j.configurationFile=${{path}}",
          "file":{{"id":"client-1.7.xml","sha1":"{log_sha}","size":{log_size},
                  "url":"{LOG_CONFIG_URL}"}},"type":"log4j2-xml"}}}},
        "minecraftArguments":"--username ${{auth_player_name}} --version ${{version_name}} --gameDir ${{game_directory}} --assetsDir ${{assets_root}} --assetIndex ${{assets_index_name}} --uuid ${{auth_uuid}} --accessToken ${{auth_access_token}} --userProperties ${{user_properties}} --userType ${{user_type}}"}}"#,
        index_sha = common::sha1(legacy_index_json().as_bytes()),
        index_size = legacy_index_json().len(),
        client_sha = common::sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        log_sha = common::sha1(LOG_CONFIG.as_bytes()),
        log_size = LOG_CONFIG.len(),
        libraries = legacy_libraries(),
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
              "url":"{VERSION_URL}","sha1":"{modern}"}},
            {{"id":"{LEGACY}","type":"release","releaseTime":"2015-12-09T11:00:00+00:00",
              "url":"{LEGACY_URL}","sha1":"{legacy}"}}
        ]}}"#,
        modern = common::sha1(version_json().as_bytes()),
        legacy = common::sha1(legacy_json().as_bytes()),
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

    http = http
        .route(LEGACY_URL, HttpResponse::ok(legacy_json()))
        .route(LEGACY_CLIENT_URL, HttpResponse::ok(CLIENT_JAR))
        .route(LEGACY_INDEX_URL, HttpResponse::ok(legacy_index_json()))
        .route(LOG_CONFIG_URL, HttpResponse::ok(LOG_CONFIG))
        .route(format!("{LIB_BASE}/legacy/lwjgl-platform.jar"), HttpResponse::ok(ANY_JAR))
        .route(format!("{LIB_BASE}/legacy/jinput.jar"), HttpResponse::ok(ANY_JAR));
    for name in ["natives-windows", "natives-windows-64", "natives-osx"] {
        http = http.route(format!("{LIB_BASE}/legacy/{name}.jar"), HttpResponse::ok(native_jar()));
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
        self.instance_on(VERSION).await
    }

    async fn ready_legacy(&self) -> InstanceId {
        self.instance_on(LEGACY).await
    }

    async fn instance_on(&self, version: &str) -> InstanceId {
        if self.ash.accounts().active_account().is_none() {
            self.ash.begin_sign_in().await.expect("device code");
            self.ash.poll_sign_in().await.expect("sign-in");
        }
        self.ash.create_instance(version, version).expect("instance").id
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

// ---- 1.8.9 ------------------------------------------------------------------
//
// One abstraction, not two. Every test above runs against the structured
// argument format; these run the same seam against the single string, and the
// only thing that changes is the metadata.

#[tokio::test]
async fn the_legacy_argument_string_is_templated() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    assert_eq!(value_of(&view.args, "--username"), Some(common::PLAYER_NAME));
    assert_eq!(value_of(&view.args, "--version"), Some(LEGACY));
    assert_eq!(value_of(&view.args, "--uuid"), Some(common::PLAYER_UUID));
    assert_eq!(value_of(&view.args, "--assetIndex"), Some("1.8"));
    // Pre-1.13 asks for two things modern versions never mention.
    assert_eq!(value_of(&view.args, "--userType"), Some("msa"));
    assert_eq!(value_of(&view.args, "--userProperties"), Some("{}"));
}

#[tokio::test]
async fn the_legacy_string_leaves_no_placeholder_behind() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    let unresolved: Vec<&String> = view.args.iter().filter(|a| a.contains("${")).collect();
    assert!(unresolved.is_empty(), "unsubstituted placeholders: {unresolved:?}");
}

#[tokio::test]
async fn a_version_with_no_argument_list_still_gets_a_classpath() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    // 1.8.9 states no JVM arguments at all - the two facts the structured
    // list replaced in 1.13 have to come from somewhere.
    let classpath = value_of(&view.args, "-cp").expect("a classpath");
    assert!(classpath.ends_with(&format!("{LEGACY}.jar")), "the client jar comes last");
    assert!(
        view.args.iter().any(|a| a.starts_with("-Djava.library.path=")),
        "LWJGL 2 loads natives off java.library.path and will not start without it"
    );
}

#[tokio::test]
async fn the_legacy_target_runs_on_java_8() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let runtime = f.ash.ensure_runtime(&id, &NullSink, &Cancel::new()).await.expect("runtime");

    // Handing 1.8.9 a modern JRE is the single most common way to make it
    // refuse to start.
    assert_eq!(runtime.component, "jre-legacy");
    assert_eq!(runtime.version_name, "8.0.412");
}

#[tokio::test]
async fn both_targets_are_served_by_one_code_path() {
    let f = fixture();
    let modern = f.ready().await;
    let legacy = f.ready_legacy().await;

    let a = f.launch(&modern).await;
    let b = f.launch(&legacy).await;

    // Same seam, same assembly, same shape out: java, JVM arguments, the main
    // class, then game arguments. If these needed different handling the
    // abstraction would have failed.
    for view in [&a, &b] {
        let main_at = view
            .args
            .iter()
            .position(|arg| arg == "net.minecraft.client.main.Main")
            .expect("a main class");
        let cp_at = view.args.iter().position(|arg| arg == "-cp").expect("-cp");
        let user_at = view.args.iter().position(|arg| arg == "--username").expect("--username");
        assert!(cp_at < main_at && main_at < user_at);
    }
    assert_ne!(a.program, b.program, "the two targets run on different runtimes");
}

// ---- natives ----------------------------------------------------------------

#[tokio::test]
async fn native_libraries_are_unpacked_where_the_game_looks_for_them() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    // Whatever `-Djava.library.path` points at is where LWJGL 2 will look, so
    // that is where the files have to be.
    let argument = view
        .args
        .iter()
        .find(|a| a.starts_with("-Djava.library.path="))
        .expect("java.library.path");
    let directory = std::path::PathBuf::from(argument.trim_start_matches("-Djava.library.path="));

    assert!(directory.join(NATIVE_FILE).is_file(), "the native library was never unpacked");
    assert_eq!(std::fs::read(directory.join(NATIVE_FILE)).expect("read"), NATIVE_BODY);
}

#[tokio::test]
async fn excluded_entries_are_not_unpacked() {
    let f = fixture();
    let id = f.ready_legacy().await;
    f.launch(&id).await;

    // `extract.exclude` says META-INF/, and a stray signature file in the
    // natives directory is at best noise.
    let natives = f.tmp.path().join(format!("depot/versions/{LEGACY}/natives"));
    assert!(!natives.join(MANIFEST_ENTRY).exists(), "an excluded entry was unpacked");
    assert!(!natives.join("META-INF").exists());
}

#[tokio::test]
async fn the_arch_templated_classifier_resolves() {
    let f = fixture();
    let id = f.ready_legacy().await;
    f.launch(&id).await;

    // `natives-windows-${arch}` is the launcher's job to fill in, and a
    // literal placeholder matches no classifier at all.
    let jar = f.tmp.path().join(if cfg!(windows) {
        "depot/libraries/legacy/natives-windows-64.jar"
    } else {
        "depot/libraries/legacy/natives-osx.jar"
    });
    assert!(jar.is_file(), "{} was never downloaded", jar.display());
}

#[tokio::test]
async fn a_modern_version_unpacks_nothing() {
    let f = fixture();
    let id = f.ready().await;
    f.launch(&id).await;

    // LWJGL 3 reads its natives straight out of the classpath, and 1.21.x
    // metadata has no `natives` map to say otherwise. Unpacking anyway would
    // be tens of megabytes written for nothing.
    let natives = f.tmp.path().join(format!("depot/versions/{VERSION}/natives"));
    let unpacked: Vec<_> = std::fs::read_dir(&natives).expect("the directory exists").collect();
    assert!(unpacked.is_empty(), "1.21.x should need no unpacking");
}

#[tokio::test]
async fn unpacking_twice_is_not_work_done_twice() {
    let f = fixture();
    let id = f.ready_legacy().await;
    f.launch(&id).await;

    let native = f.tmp.path().join(format!("depot/versions/{LEGACY}/natives/{NATIVE_FILE}"));
    let first = std::fs::metadata(&native).expect("unpacked").modified().expect("mtime");

    f.ash.stop_game(&id);
    f.launch(&id).await;

    assert_eq!(
        std::fs::metadata(&native).expect("still there").modified().expect("mtime"),
        first,
        "the native library was rewritten on the second launch"
    );
}

// ---- log4j ------------------------------------------------------------------

#[tokio::test]
async fn the_log4j_configuration_mojang_publishes_is_applied() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    // For 1.7 to 1.11 this file *is* the Log4Shell mitigation: those versions
    // ship a log4j too old for `formatMsgNoLookups`, so the patched
    // configuration is the whole fix.
    let argument = view
        .args
        .iter()
        .find(|a| a.starts_with("-Dlog4j.configurationFile="))
        .expect("the log4j configuration argument");
    let path = std::path::PathBuf::from(argument.trim_start_matches("-Dlog4j.configurationFile="));

    assert!(path.is_file(), "{} was never downloaded", path.display());
    let contents = std::fs::read_to_string(&path).expect("read");
    assert!(contents.contains("RegexFilter"), "this is not the patched configuration");
}

#[tokio::test]
async fn the_log4j_argument_comes_before_anything_else() {
    let f = fixture();
    let id = f.ready_legacy().await;

    let view = f.launch(&id).await;

    assert!(
        view.args[0].starts_with("-Dlog4j.configurationFile="),
        "the mitigation has to be in force before the JVM is told anything else, got {:?}",
        view.args[0]
    );
}

#[tokio::test]
async fn a_tampered_log4j_configuration_is_refused() {
    let f = fixture();
    let http = serving().route(LOG_CONFIG_URL, HttpResponse::ok("<Configuration/>"));
    let ash = Ash::new(
        Config::rooted_at(f.tmp.path().join("other")),
        http as Arc<dyn HttpPort>,
        Arc::clone(&f.store) as Arc<dyn CredentialStore>,
        Arc::clone(&f.process) as Arc<dyn ProcessPort>,
        "test-client",
    );
    ash.begin_sign_in().await.expect("device code");
    ash.poll_sign_in().await.expect("sign-in");
    let instance = ash.create_instance("legacy", LEGACY).expect("instance");

    let err =
        ash.launch(&instance.id, &NullSink, &Cancel::new()).await.expect_err("tampered config");

    // Swapping the mitigation for an empty configuration would silently
    // re-open Log4Shell, so it is verified like every other artifact.
    assert_eq!(err.kind(), "verification_failed");
    assert!(f.process.spawned().is_empty());
}

// ---- reading what the game says ---------------------------------------------

#[tokio::test]
async fn the_log_is_readable_even_though_log4j_writes_xml() {
    let f = fixture_with(FakeProcessPort::new().set_log(&[
        r#"<log4j:Event logger="net.minecraft.Foo" level="INFO" thread="Render thread">"#,
        r#"<log4j:Message><![CDATA[Setting user: oogz]]></log4j:Message>"#,
        r#"</log4j:Event>"#,
    ]));
    let id = f.ready_legacy().await;
    f.launch(&id).await;

    // Applying Mojang's configuration is what turns the console into XML.
    // Showing a player that raw would be showing them nothing.
    assert_eq!(f.ash.game_log(&id), ["[Render thread/INFO]: Setting user: oogz"]);
}
