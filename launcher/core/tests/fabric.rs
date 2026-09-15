//! Fabric on the modern version target: preparing it, verifying it, sharing
//! it, and launching it.
//!
//! Every artifact here is built in this file and its hash computed from the
//! bytes, so nothing can drift. The pins are handed to `Config` for the same
//! reason - the real ones name real jars, and a test that used them would
//! have to serve several megabytes of somebody else's binaries to prove
//! anything at all.
//!
//! The Maven these tests point at does not exist. `FakeHttp` panics on a URL
//! it has no route for, so a fetch ash was not supposed to make fails loudly
//! here rather than quietly succeeding against the real one.

use std::sync::{Arc, OnceLock};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{
    Ash, AshError, Cancel, Config, InstanceId, Loader, LoaderPin, NullSink, PinnedFile,
    PinnedLibrary, VERSION_MANIFEST_URL,
};

mod common;

const VERSION: &str = "1.21.11";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.11.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const CLIENT_JAR: &[u8] = b"pretend this is the client jar";

const MAVEN: &str = "https://maven.test/";
const DOCUMENT_URL: &str =
    "https://maven.test/net/fabricmc/fabric-loader/9.9.9/fabric-loader-9.9.9.json";

const KNOT: &str = "net.fabricmc.loader.impl.launch.knot.KnotClient";

/// The vanilla entry the loader replaces.
///
/// Deliberately left unrouted by [`serving`]: a modded instance must never
/// fetch it, so if the merge ever stops replacing it the download hits a URL
/// with no route and panics rather than quietly passing. The one test that
/// wants a vanilla instance adds the route itself.
const VANILLA_ASM: &str = "org.ow2.asm:asm:9.6";
const VANILLA_ASM_URL: &str = "https://libraries.minecraft.net/org/ow2/asm/asm/9.6/asm-9.6.jar";
const VANILLA_ASM_JAR: &[u8] = b"pretend this is asm 9.6";

const ASM: &str = "org.ow2.asm:asm:9.10.1";
const MIXIN: &str = "net.fabricmc:sponge-mixin:0.17.4";
const INTERMEDIARY: &str = "net.fabricmc:intermediary:1.21.11";
const LOADER: &str = "net.fabricmc:fabric-loader:9.9.9";
const API: &str = "net.fabricmc.fabric-api:fabric-api:0.1.0+1.21.11";

const ASM_JAR: &[u8] = b"pretend this is asm 9.10.1";
const MIXIN_JAR: &[u8] = b"pretend this is sponge-mixin";
const INTERMEDIARY_JAR: &[u8] = b"pretend this is the intermediary";
const LOADER_JAR: &[u8] = b"pretend this is fabric loader";
const API_JAR: &[u8] = b"pretend this is fabric api";

// ---- fixtures ---------------------------------------------------------------

/// Where a coordinate's jar lives, stated independently of the code under
/// test.
///
/// Deliberately a second implementation rather than a call into ash: if the
/// path ash builds ever stops matching the one a Maven actually serves, the
/// download has no route and the test says so.
fn maven_url(coordinate: &str) -> String {
    let mut parts = coordinate.split(':');
    let group = parts.next().expect("a group").replace('.', "/");
    let artifact = parts.next().expect("an artifact");
    let version = parts.next().expect("a version");
    format!("{MAVEN}{group}/{artifact}/{version}/{artifact}-{version}.jar")
}

fn depot_relative(coordinate: &str) -> String {
    maven_url(coordinate).replace(MAVEN, "libraries/")
}

/// The loader's own launcher metadata, in the shape the real one has:
/// `mainClass` an object, every library carrying its own hash and size, and
/// a development entry that must not reach a player's classpath.
fn document() -> String {
    format!(
        r#"{{"version":2,"min_java_version":21,
          "libraries":{{
            "common":[
              {{"name":"{ASM}","url":"{MAVEN}","sha1":"{asm_sha}","size":{asm_size}}},
              {{"name":"{MIXIN}","url":"{MAVEN}","sha1":"{mixin_sha}","size":{mixin_size}}}
            ],
            "client":[],
            "server":[],
            "development":[
              {{"name":"io.github.llamalad7:mixinextras-fabric:0.5.5","url":"{MAVEN}",
                "sha1":"deadbeef","size":1}}
            ]
          }},
          "mainClass":{{"client":"{KNOT}",
                        "server":"net.fabricmc.loader.impl.launch.knot.KnotServer"}}}}"#,
        asm_sha = common::sha1(ASM_JAR),
        asm_size = ASM_JAR.len(),
        mixin_sha = common::sha1(MIXIN_JAR),
        mixin_size = MIXIN_JAR.len(),
    )
}

/// The pins under test, as `Config` takes them.
///
/// The hashes are of bytes this file builds, so they cannot be written as
/// constants. Leaking is how a value computed at runtime becomes the
/// `'static` one a pin holds, and a test process is exactly where that costs
/// nothing.
fn pins() -> &'static [LoaderPin] {
    static PINS: OnceLock<&'static [LoaderPin]> = OnceLock::new();

    PINS.get_or_init(|| {
        let leak = |text: String| -> &'static str { Box::leak(text.into_boxed_str()) };
        let library = |name: &'static str, jar: &'static [u8]| PinnedLibrary {
            name,
            repository: MAVEN,
            sha1: leak(common::sha1(jar)),
            size: jar.len() as u64,
        };

        let document = document();
        let libraries: &'static [PinnedLibrary] = Box::leak(Box::new([
            library(INTERMEDIARY, INTERMEDIARY_JAR),
            library(LOADER, LOADER_JAR),
        ]));
        let bundled: &'static [PinnedLibrary] = Box::leak(Box::new([library(API, API_JAR)]));

        let pins: &'static [LoaderPin] = Box::leak(Box::new([LoaderPin {
            loader: Loader::Fabric,
            version_id: VERSION,
            loader_version: "9.9.9",
            document: PinnedFile {
                url: DOCUMENT_URL,
                sha1: leak(common::sha1(document.as_bytes())),
                size: document.len() as u64,
            },
            libraries,
            jvm_arguments: &["-DFabricMcEmu= net.minecraft.client.main.Main "],
            bundled_mods: bundled,
        }]));
        pins
    })
}

fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
        "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},
                      "url":"{CLIENT_URL}"}}}},
        "libraries":[{{"name":"{VANILLA_ASM}","downloads":{{"artifact":{{
            "path":"org/ow2/asm/asm/9.6/asm-9.6.jar","sha1":"{vanilla_asm_sha}",
            "size":{vanilla_asm_size},"url":"{VANILLA_ASM_URL}"}}}}}}],
        "arguments":{{"jvm":["-cp","${{classpath}}"],
                      "game":["--username","${{auth_player_name}}"]}}}}"#,
        client_sha = common::sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        vanilla_asm_sha = common::sha1(VANILLA_ASM_JAR),
        vanilla_asm_size = VANILLA_ASM_JAR.len(),
    )
}

fn manifest_json() -> String {
    format!(
        r#"{{"latest":{{"release":"{VERSION}","snapshot":"{VERSION}"}},"versions":[
            {{"id":"{VERSION}","type":"release","releaseTime":"2025-12-09T09:00:00+00:00",
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
            .route(DOCUMENT_URL, HttpResponse::ok(document()))
            .route(maven_url(ASM), HttpResponse::ok(ASM_JAR))
            .route(maven_url(MIXIN), HttpResponse::ok(MIXIN_JAR))
            .route(maven_url(INTERMEDIARY), HttpResponse::ok(INTERMEDIARY_JAR))
            .route(maven_url(LOADER), HttpResponse::ok(LOADER_JAR))
            .route(maven_url(API), HttpResponse::ok(API_JAR)),
    ))
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    tmp: tempfile::TempDir,
}

fn fixture_with(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(
        Config { loaders: pins(), ..Config::rooted_at(tmp.path()) },
        Arc::clone(&http) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    );
    Fixture { ash, http, tmp }
}

fn fixture() -> Fixture {
    fixture_with(serving())
}

impl Fixture {
    async fn modded(&self) -> InstanceId {
        self.ash.begin_sign_in().await.expect("device code");
        self.ash.poll_sign_in().await.expect("sign-in");
        self.ash.create_instance("modded", VERSION, Loader::Fabric).expect("instance").id
    }

    async fn prepare(&self, id: &InstanceId) {
        self.ash.prepare_instance(id, &NullSink, &Cancel::new()).await.expect("prepared");
    }
}

// ---- preparing --------------------------------------------------------------

#[tokio::test]
async fn a_fabric_instance_fetches_the_loader_the_intermediary_and_the_api() {
    let f = fixture();
    let id = f.modded().await;

    f.prepare(&id).await;

    // The player listed none of this. The pinned document named ASM and
    // Mixin, and the pin named the rest.
    for coordinate in [ASM, MIXIN, INTERMEDIARY, LOADER, API] {
        let path = f.tmp.path().join("depot").join(depot_relative(coordinate));
        assert!(path.is_file(), "{coordinate} is not in the depot");
    }
    // And the vanilla client jar is still the version target's own.
    assert!(f.tmp.path().join("depot/versions/1.21.11/1.21.11.jar").is_file());
}

#[tokio::test]
async fn the_api_lands_where_the_loader_looks() {
    let f = fixture();
    let id = f.modded().await;

    f.prepare(&id).await;

    // A loader reads the instance's own mods directory and nothing else, so
    // a bundled mod that only reached the depot would not load.
    let mods = f.ash.game_directory(&id).join("mods");
    assert!(
        mods.join("fabric-api-0.1.0+1.21.11.jar").is_file(),
        "the api is not in the instance: {:?}",
        std::fs::read_dir(&mods).map(|d| d.flatten().map(|e| e.file_name()).collect::<Vec<_>>())
    );
}

#[tokio::test]
async fn a_bundled_mod_replaces_the_version_that_was_there_before() {
    let f = fixture();
    let id = f.modded().await;
    let mods = f.ash.game_directory(&id).join("mods");

    // What an earlier ash release would have left behind, and a mod the
    // player put there themselves.
    std::fs::write(mods.join("fabric-api-0.0.1+1.21.11.jar"), b"an older api").unwrap();
    std::fs::write(mods.join("sodium-1.2.3.jar"), b"a mod the player chose").unwrap();

    f.prepare(&id).await;

    // A loader refuses to start when two files claim one mod id, so moving
    // a pin has to replace the jar rather than sit beside it.
    assert!(
        !mods.join("fabric-api-0.0.1+1.21.11.jar").exists(),
        "the previous api jar is still there, so the loader sees two"
    );
    assert!(mods.join("fabric-api-0.1.0+1.21.11.jar").is_file());
    // And removal reaches no further than ash's own artifact.
    assert!(mods.join("sodium-1.2.3.jar").is_file(), "a player's own mod was removed");
}

#[tokio::test]
async fn preparing_a_modded_instance_leaves_the_rest_of_the_game_directory_alone() {
    let f = fixture();
    let id = f.modded().await;
    let game = f.ash.game_directory(&id);
    std::fs::write(game.join("mods").join("mine.jar"), b"a mod the player supplied").unwrap();
    std::fs::create_dir_all(game.join("saves").join("My World")).unwrap();

    f.prepare(&id).await;

    assert!(game.join("mods").join("mine.jar").is_file(), "a player's own mod was removed");
    assert!(game.join("saves").join("My World").is_dir(), "a world was touched");
}

#[tokio::test]
async fn ash_never_asks_fabric_meta_for_a_profile() {
    let f = fixture();
    let id = f.modded().await;

    f.prepare(&id).await;

    let requested = f.http.requested();
    assert!(!requested.is_empty(), "the test proves nothing if nothing was requested");
    // Fabric Meta's profile is generated per request, so it can never be
    // verified against a published hash. ash builds the profile itself
    // instead, which is the whole of ADR-0014.
    for url in &requested {
        assert!(!url.contains("meta.fabricmc.net"), "ash asked Fabric Meta: {url}");
        assert!(!url.contains("/profile/"), "ash asked for a generated profile: {url}");
    }
    // It did read the one document that *is* immutable.
    assert!(requested.iter().any(|url| url == DOCUMENT_URL));
}

// ---- verification -----------------------------------------------------------

#[tokio::test]
async fn a_corrupt_loader_artifact_fails_the_way_a_corrupt_game_file_does() {
    // Twice: the depot retries a hash mismatch once before giving up, and a
    // single bad response would be repaired rather than reported.
    let http = serving().route_sequence(
        maven_url(LOADER),
        vec![
            HttpResponse::ok(b"not the loader".to_vec()),
            HttpResponse::ok(b"still not the loader".to_vec()),
        ],
    );
    let f = fixture_with(http);
    let id = f.modded().await;

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("a corrupt loader jar is refused");

    assert_eq!(err.kind(), "verification_failed");
    match err {
        AshError::VerificationFailed { path } => {
            assert!(path.contains("fabric-loader"), "the message names the file: {path}");
        }
        other => panic!("expected a verification failure, got {other:?}"),
    }
}

#[tokio::test]
async fn a_loader_document_that_does_not_match_its_pin_is_refused() {
    let http = serving().route(DOCUMENT_URL, HttpResponse::ok(r#"{"version":2}"#));
    let f = fixture_with(http);
    let id = f.modded().await;

    let err = f
        .ash
        .plan_instance(&id)
        .await
        .expect_err("a document that is not the pinned one is refused");

    // Refused on its hash, before anything it says is read - a document ash
    // did not pin could name any library anywhere.
    assert_eq!(err.kind(), "verification_failed");
}

// ---- sharing and offline ----------------------------------------------------

#[tokio::test]
async fn a_second_modded_instance_on_the_same_target_needs_nothing_new() {
    let f = fixture();
    let first = f.modded().await;
    f.prepare(&first).await;

    let second = f.ash.create_instance("another", VERSION, Loader::Fabric).expect("instance").id;
    let plan = f.ash.plan_instance(&second).await.expect("planned");

    assert!(plan.total_files > 0, "the test proves nothing if the plan is empty");
    assert_eq!(plan.missing_files, 0, "loader artifacts are shared through the depot");
}

#[tokio::test]
async fn a_prepared_modded_instance_plans_with_no_network() {
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;

    // A new Ash over the same directories, with nothing reachable at all.
    let offline = Ash::new(
        Config { loaders: pins(), ..Config::rooted_at(f.tmp.path()) },
        FakeHttp::offline() as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    );

    let plan = offline.plan_instance(&id).await.expect("a prepared instance plans offline");

    assert_eq!(plan.missing_files, 0);
    assert_eq!(plan.version_id, "fabric-loader-9.9.9-1.21.11");
}

// ---- launching --------------------------------------------------------------

#[tokio::test]
async fn the_command_line_starts_the_loader_rather_than_the_game() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    assert!(
        view.args.contains(&KNOT.to_owned()),
        "the loader is not the main class: {:?}",
        view.args
    );
    assert!(
        !view.args.contains(&"net.minecraft.client.main.Main".to_owned()),
        "the vanilla main class is still being started: {:?}",
        view.args
    );
    // Fabric's one cosmetic JVM argument, which exists so that anything
    // reading the process command line still sees a vanilla-looking game.
    assert!(view.args.iter().any(|a| a.starts_with("-DFabricMcEmu=")));
}

#[tokio::test]
async fn the_classpath_carries_the_loader_and_ends_at_the_vanilla_client_jar() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");
    let classpath = value_of(&view.args, "-cp").expect("a classpath");

    for coordinate in [ASM, MIXIN, INTERMEDIARY, LOADER] {
        assert!(
            classpath.contains(&depot_relative(coordinate).replace("libraries/", "")),
            "{coordinate} is not on the classpath"
        );
    }
    // The merged document's id names a profile with no jar behind it, so the
    // last entry has to be the version target's own client jar. Asserted on
    // the whole string rather than by splitting it: a Windows classpath is
    // separated by `;` and its paths contain `:`, so splitting on either is
    // a trap.
    assert!(
        classpath.ends_with("1.21.11.jar"),
        "the client jar is not last on the classpath: {classpath}"
    );
    assert!(
        !classpath.contains("fabric-loader-9.9.9-1.21.11.jar"),
        "the classpath names a jar that does not exist: {classpath}"
    );
}

#[tokio::test]
async fn the_loaders_asm_replaces_the_one_the_game_shipped_with() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");
    let classpath = value_of(&view.args, "-cp").expect("a classpath");

    // Two versions of ASM on one classpath is a loader that may or may not
    // start depending on which the JVM reaches first.
    assert!(classpath.contains("asm-9.10.1.jar"));
    assert!(!classpath.contains("asm-9.6.jar"), "the vanilla ASM survived: {classpath}");
}

#[tokio::test]
async fn a_vanilla_instance_on_the_same_version_target_is_untouched() {
    // The one fixture that routes vanilla's own ASM, because a vanilla
    // instance is the only thing that should ever ask for it.
    let f = fixture_with(serving().route(VANILLA_ASM_URL, HttpResponse::ok(VANILLA_ASM_JAR)));
    f.ash.begin_sign_in().await.expect("device code");
    f.ash.poll_sign_in().await.expect("sign-in");
    let plain = f.ash.create_instance("plain", VERSION, Loader::Vanilla).expect("instance").id;

    let view = f.ash.preview_launch(&plain, &NullSink, &Cancel::new()).await.expect("previewed");

    assert!(view.args.contains(&"net.minecraft.client.main.Main".to_owned()));
    assert!(!view.args.iter().any(|a| a.starts_with("-DFabricMcEmu=")));
    assert!(!f
        .ash
        .game_directory(&plain)
        .join("mods")
        .join("fabric-api-0.1.0+1.21.11.jar")
        .exists());
}

// ---- what cannot be created -------------------------------------------------

#[test]
fn a_loader_ash_has_no_pin_for_cannot_be_chosen() {
    let f = fixture();

    let err = f
        .ash
        .create_instance("doomed", "1.16.5", Loader::Fabric)
        .expect_err("an unpinned pairing is refused");

    assert_eq!(err.kind(), "loader_unavailable");
    assert!(f.ash.instances().unwrap().is_empty(), "nothing was created");
    // The player is told which way out they have, without a path or a URL.
    let message = err.user_message();
    assert!(message.contains("1.16.5"), "{message}");
    assert!(!message.contains('/'), "leaked a path or url: {message}");
}

#[test]
fn only_the_loaders_a_version_target_can_run_are_offered() {
    let f = fixture();

    assert_eq!(f.ash.loaders_for(VERSION), [Loader::Vanilla, Loader::Fabric]);
    assert_eq!(f.ash.loaders_for("1.16.5"), [Loader::Vanilla]);
}

fn value_of<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| a == flag)?;
    args.get(at + 1).map(String::as_str)
}
