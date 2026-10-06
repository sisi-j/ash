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

use std::path::PathBuf;
use std::sync::{Arc, OnceLock};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::servers::FakeServerPort;
use ash_core::{
    Ash, AshError, AshFeatures, Cancel, Config, InstanceId, Loader, LoaderPin, NullSink,
    PinnedFile, PinnedLibrary, VERSION_MANIFEST_URL,
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

/// ash's own client, as the installer leaves it beside the executable.
///
/// Never routed, deliberately. This jar does not come over the network at
/// all, so a `FakeHttp` that has no route for it is part of the proof: if
/// preparing ever tried to fetch ash's client, the fake would panic.
const ASH_CLIENT: &str = "ash-client-1.21.11.jar";
const ASH_CLIENT_JAR: &[u8] = b"pretend this is ash's own client";

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
            mirror: None,
            sha1: leak(common::sha1(jar)),
            size: jar.len() as u64,
            natives: &[],
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
            client_jar: Some(ASH_CLIENT),
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
    process: Arc<FakeProcessPort>,
    tmp: tempfile::TempDir,
}

/// Put ash's own client where an installed ash would have it.
///
/// The installer's job in real life, and the fixture's here. Preparing a
/// modded instance without it is its own test below.
fn install_ash_client(client_root: &std::path::Path) {
    std::fs::create_dir_all(client_root).expect("the client root");
    std::fs::write(client_root.join(ASH_CLIENT), ASH_CLIENT_JAR).expect("ash's client");
}

fn fixture_with(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let config = Config { loaders: pins(), ..Config::rooted_at(tmp.path()) };
    install_ash_client(&config.client_root);
    let process = FakeProcessPort::new();
    let ash = Ash::new(
        config,
        Arc::clone(&http) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        Arc::clone(&process) as Arc<dyn ProcessPort>,
        FakeServerPort::new(),
        "test-client",
    );
    Fixture { ash, http, process, tmp }
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

/// The value of a `-Dname=value` JVM argument, if the command line has one.
fn property<'a>(args: &'a [String], name: &str) -> Option<&'a str> {
    let prefix = format!("-D{name}=");
    args.iter().find_map(|a| a.strip_prefix(&prefix))
}

#[tokio::test]
async fn the_api_reaches_the_loader_from_the_depot_and_never_the_mods_folder() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    let added = property(&view.args, "fabric.addMods").expect("ash's jars were not handed over");
    let api = f.tmp.path().join("depot").join(depot_relative(API));
    assert!(added.contains(&api.display().to_string()), "the api is not among them: {added}");
    let mods = f.ash.game_directory(&id).join("mods");
    assert_eq!(
        std::fs::read_dir(&mods).unwrap().count(),
        0,
        "ash put something in the mods folder"
    );
}

#[tokio::test]
async fn a_players_jar_survives_preparing_and_launching_even_named_like_a_bundled_mod() {
    let f = fixture();
    let id = f.modded().await;
    let mods = f.ash.game_directory(&id).join("mods");
    // A player's own download of the same mod: an older one, and one with
    // exactly ash's file name and different bytes. Phase 2 deleted the first
    // and overwrote the second.
    let players = [
        ("fabric-api-0.0.1+1.21.11.jar", &b"the player's older api"[..]),
        ("fabric-api-0.1.0+1.21.11.jar", &b"the player's own build"[..]),
        ("sodium-1.2.3.jar", &b"a mod the player chose"[..]),
    ];
    for (name, bytes) in players {
        std::fs::write(mods.join(name), bytes).unwrap();
    }

    f.prepare(&id).await;
    f.prepare(&id).await;
    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launched");

    for (name, bytes) in players {
        assert_eq!(
            std::fs::read(mods.join(name)).ok().as_deref(),
            Some(bytes),
            "{name} was touched"
        );
    }
}

#[tokio::test]
async fn a_phase_2_instance_loses_exactly_the_jars_ash_put_in_its_mods_folder() {
    let f = fixture();
    let id = f.modded().await;
    let mods = f.ash.game_directory(&id).join("mods");
    // What Phase 2's preparation left: the api with its pinned bytes, and
    // ash's client by its fixed name. And a player's mod beside them.
    std::fs::write(mods.join("fabric-api-0.1.0+1.21.11.jar"), API_JAR).unwrap();
    std::fs::write(mods.join(ASH_CLIENT), b"a Phase 2 client").unwrap();
    std::fs::write(mods.join("sodium-1.2.3.jar"), b"a mod the player chose").unwrap();

    f.prepare(&id).await;

    let left: Vec<String> = std::fs::read_dir(&mods)
        .unwrap()
        .flatten()
        .filter_map(|e| e.file_name().into_string().ok())
        .collect();
    assert_eq!(left, ["sodium-1.2.3.jar"]);
}

#[tokio::test]
async fn the_mods_folder_is_moved_to_an_empty_one_ash_owns() {
    let f = fixture();
    let id = f.modded().await;
    std::fs::write(f.ash.game_directory(&id).join("mods").join("sodium.jar"), b"a mod").unwrap();

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    // The loader cannot be told to read no folder, only another one, so a
    // player's jar loads only when that folder is the instance's own.
    let folder = PathBuf::from(property(&view.args, "fabric.modsFolder").expect("not moved"));
    assert!(folder.is_absolute(), "{folder:?}");
    assert!(folder.starts_with(&f.ash.config().data_root), "not ash's own: {folder:?}");
    assert_eq!(
        std::fs::read_dir(&folder).unwrap().count(),
        0,
        "the folder the loader reads has something in it"
    );
    assert!(!property(&view.args, "fabric.addMods").unwrap().contains("sodium"));
}

#[tokio::test]
async fn both_properties_sit_with_the_jvm_arguments_before_the_main_class() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    let main = view.args.iter().position(|a| a == KNOT).expect("the loader's main class");
    for name in ["fabric.addMods", "fabric.modsFolder"] {
        let at = view.args.iter().position(|a| a.starts_with(&format!("-D{name}="))).expect(name);
        assert!(at < main, "{name} comes after the main class, so the game gets it, not the JVM");
    }
}

#[tokio::test]
async fn preparing_a_modded_instance_leaves_the_rest_of_the_game_directory_alone() {
    let f = fixture();
    let id = f.modded().await;
    let game = f.ash.game_directory(&id);
    // Everything a player would be upset to lose, in and around the directory
    // ash is about to write two jars into.
    std::fs::write(game.join("mods").join("mine.jar"), b"a mod the player supplied").unwrap();
    std::fs::create_dir_all(game.join("saves").join("My World")).unwrap();
    std::fs::create_dir_all(game.join("resourcepacks")).unwrap();
    std::fs::write(game.join("resourcepacks").join("pack.zip"), b"a pack").unwrap();
    std::fs::create_dir_all(game.join("screenshots")).unwrap();
    std::fs::write(game.join("screenshots").join("shot.png"), b"a screenshot").unwrap();
    std::fs::write(game.join("options.txt"), b"the player's settings").unwrap();

    f.prepare(&id).await;

    // Present *and* unchanged: a file ash had overwritten with something else
    // would still pass an `is_file` check.
    let unchanged = |relative: &str, expected: &[u8]| {
        assert_eq!(
            std::fs::read(game.join(relative)).ok().as_deref(),
            Some(expected),
            "{relative} was changed or removed"
        );
    };
    unchanged("mods/mine.jar", b"a mod the player supplied");
    unchanged("resourcepacks/pack.zip", b"a pack");
    unchanged("screenshots/shot.png", b"a screenshot");
    unchanged("options.txt", b"the player's settings");
    assert!(game.join("saves").join("My World").is_dir(), "a world was touched");
}

/// The client owns `config/ash.properties` and the launcher never writes it -
/// a rule of this phase rather than an accident of it. Synced settings are
/// Phase 4, and a second writer brings conflict rules that belong to that
/// design; the client's file format is not the launcher's to invent early.
#[tokio::test]
async fn the_clients_own_settings_file_is_left_exactly_as_the_client_wrote_it() {
    let f = fixture();
    let id = f.modded().await;
    let game = f.ash.game_directory(&id);
    let settings = game.join("config").join("ash.properties");
    std::fs::create_dir_all(settings.parent().unwrap()).unwrap();
    // Nothing a launcher that parsed the file would leave alone: a comment, a
    // setting from a future client, and no newline at the end.
    let written = b"# turned off for recording\nfps-readout.enabled=false\nsome-future.setting=42";
    std::fs::write(&settings, written).unwrap();

    f.prepare(&id).await;
    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launched");

    assert_eq!(
        std::fs::read(&settings).ok().as_deref(),
        Some(&written[..]),
        "the launcher changed or removed the client's settings file"
    );
}

// ---- ash's own client -------------------------------------------------------

#[tokio::test]
async fn ashs_own_client_reaches_the_loader_from_the_installation() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    // By path, from where the installer put it: an update to ash is then the
    // client the game gets, with no copy anywhere to fall out of date.
    let client = f.ash.config().client_root.join(ASH_CLIENT);
    let added = property(&view.args, "fabric.addMods").expect("ash's jars were not handed over");
    assert!(added.contains(&client.display().to_string()), "the client is not among them: {added}");
    assert!(!f.ash.game_directory(&id).join("mods").join(ASH_CLIENT).exists());
    // It came off disk, not off the network. `serving` has no route for it, so
    // a fetch would have panicked - but saying so here is what stops a route
    // being added later without anyone weighing what it would mean.
    assert!(
        !f.http.requested().iter().any(|url| url.contains("ash-client")),
        "ash's own client was fetched; it ships in the installer"
    );
}

#[tokio::test]
async fn a_vanilla_instance_gets_no_ash_client() {
    let f = fixture();
    f.ash.begin_sign_in().await.expect("device code");
    f.ash.poll_sign_in().await.expect("sign-in");
    let plain = f.ash.create_instance("plain", VERSION, Loader::Vanilla).expect("instance").id;
    // The vanilla library a modded merge would have replaced.
    f.http.route(VANILLA_ASM_URL, HttpResponse::ok(VANILLA_ASM_JAR));
    // The test proves nothing if there was no client to hand over in the
    // first place - it would pass just as well against a fixture that never
    // wrote one.
    assert!(f.ash.config().client_root.join(ASH_CLIENT).is_file());

    let view = f.ash.preview_launch(&plain, &NullSink, &Cancel::new()).await.expect("previewed");

    assert_eq!(
        property(&view.args, "fabric.addMods"),
        None,
        "a vanilla instance was given ash's jars"
    );
    assert_eq!(property(&view.args, "fabric.modsFolder"), None);
    assert!(!f.ash.game_directory(&plain).join("mods").join(ASH_CLIENT).exists());
}

#[tokio::test]
async fn an_installation_missing_ashs_client_is_refused_rather_than_prepared_without_it() {
    let f = fixture();
    let id = f.modded().await;
    // Exactly what a damaged installation looks like from here.
    std::fs::remove_file(f.ash.config().client_root.join(ASH_CLIENT)).unwrap();

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("a modded instance with no client is refused");

    assert_eq!(err.kind(), "client_missing");
    // ash's problem, said as ash's problem, with nowhere for the player to go
    // hunting through their own setup.
    let message = err.user_message();
    assert!(message.contains("ash"), "{message}");
    assert!(!message.contains('/') && !message.contains('\\'), "leaked a path: {message}");
    assert!(!err.is_retryable(), "retrying will not put the file back");
}

#[tokio::test]
async fn a_missing_client_stops_the_launch_rather_than_starting_a_game_without_one() {
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;
    // Removed after a good prepare, so the instance is otherwise ready and the
    // only thing wrong with it is the thing being tested.
    std::fs::remove_file(f.ash.config().client_root.join(ASH_CLIENT)).unwrap();

    let err = f
        .ash
        .launch(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("ash refuses rather than starting a game with no client in it");

    assert_eq!(err.kind(), "client_missing");
    assert!(f.process.spawned().is_empty(), "the game was started anyway");
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
        FakeServerPort::new(),
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

// ---- the load report ----------------------------------------------------------
//
// The client writes `ash/load-report.json` into the game directory each time
// it starts, naming which of ash's features loaded and which degraded. The
// launcher reads it before the next play. One way only: the launcher never
// writes it. See ADR-0017.

/// What the client writes, as it would write it.
fn write_load_report(f: &Fixture, id: &InstanceId, json: &str) {
    let path = f.ash.game_directory(id).join("ash").join("load-report.json");
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, json).unwrap();
}

/// What the client writes for a session where the FPS readout loaded and
/// toggle sprint degraded. One file, and it is the contract: these tests parse
/// it, and the client's own test in `client/shared` must produce it byte for
/// byte - so the two sides cannot each pass while disagreeing about the format.
const TOGGLE_SPRINT_DEGRADED: &str = include_str!("fixtures/load-report.json");

#[tokio::test]
async fn a_feature_that_degraded_last_session_is_surfaced_before_the_next_play() {
    let f = fixture();
    let id = f.modded().await;
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);

    let notice = f.ash.degradation_notice(&id).expect("read").expect("a notice");

    assert_eq!(notice.features, ["Toggle sprint"], "only the degraded feature is named");
    assert!(notice.message.contains("Toggle sprint"), "{}", notice.message);
}

#[tokio::test]
async fn the_notice_says_the_fault_is_ash_s_and_sends_nobody_hunting_through_their_setup() {
    let f = fixture();
    let id = f.modded().await;
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);

    let message = f.ash.degradation_notice(&id).unwrap().unwrap().message;

    // Whose problem it is, and that the game still works without it.
    assert!(message.contains("problem with ash"), "does not say it is ash's problem: {message}");
    assert!(message.contains("still play"), "does not say the game still works: {message}");
    // None of the things a player would go and try, each of which would
    // waste their evening: the fix is an ash update, not their machine.
    for hunt in
        ["reinstall", "Java", "driver", "your mods", "your files", "check your", "restart your"]
    {
        assert!(!message.contains(hunt), "sends the player hunting ({hunt:?}): {message}");
    }
    assert!(!message.contains('\n'), "not one clean line: {message}");
}

#[tokio::test]
async fn the_notice_lasts_until_a_session_where_the_feature_loaded() {
    let f = fixture();
    let id = f.modded().await;
    // A first launch reports nothing - the client has not run yet to say.
    // That is the accepted cost of reporting in the launcher (ADR-0017).
    assert_eq!(f.ash.degradation_notice(&id).unwrap(), None);

    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);
    // Asked again, as a restarted launcher would: it is still there, because
    // it is read from what the client wrote, not held in the launcher.
    assert!(f.ash.degradation_notice(&id).unwrap().is_some());
    assert!(f.ash.degradation_notice(&id).unwrap().is_some(), "the notice went away on its own");

    // A later session - an ash update, say - where it loaded.
    write_load_report(
        &f,
        &id,
        r#"{ "client": "0.1.1", "features": [
            { "id": "fps-readout", "name": "FPS readout", "status": "loaded" },
            { "id": "toggle-sprint", "name": "Toggle sprint", "status": "loaded" } ] }"#,
    );
    assert_eq!(f.ash.degradation_notice(&id).unwrap(), None, "fixed, but still reported");
}

#[tokio::test]
async fn a_feature_the_player_switched_off_is_not_reported_as_a_problem() {
    let f = fixture();
    let id = f.modded().await;
    // Beside one that did degrade, so the report has to be read and understood
    // for this to pass - an unreadable report would name nothing at all.
    write_load_report(
        &f,
        &id,
        r#"{ "client": "0.1.0", "features": [
            { "id": "fps-readout", "name": "FPS readout", "status": "off" },
            { "id": "toggle-sprint", "name": "Toggle sprint", "status": "degraded" } ] }"#,
    );

    let notice = f.ash.degradation_notice(&id).unwrap().expect("the report was not read");
    assert_eq!(
        notice.features,
        ["Toggle sprint"],
        "a switched-off feature was reported as a problem"
    );
}

#[tokio::test]
async fn a_launch_records_the_last_session_s_load_report_in_ash_s_own_log() {
    // So it arrives in anything a player sends in, whether or not they
    // noticed the notice.
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);
    let report = f.ash.game_directory(&id).join("ash").join("load-report.json");
    let written = std::fs::read(&report).unwrap();

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launched");

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    let line =
        log.lines().find(|l| l.contains("load-report")).expect("no load report in ash's log");
    assert!(line.contains("toggle-sprint=degraded"), "{line}");
    assert!(line.contains("fps-readout=loaded"), "{line}");
    assert!(line.contains("client=0.1.0"), "{line}");
    // And the channel runs one way: the launcher read it and left it alone.
    assert_eq!(std::fs::read(&report).unwrap(), written, "the launcher wrote to the load report");
}

#[tokio::test]
async fn a_report_the_launcher_cannot_read_costs_the_notice_and_not_the_play() {
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;
    write_load_report(&f, &id, "{ this is not what the client writes");

    assert_eq!(f.ash.degradation_notice(&id).unwrap(), None);
    f.ash
        .launch(&id, &NullSink, &Cancel::new())
        .await
        .expect("an unreadable report stopped a launch");

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    assert!(log.contains("load-report-unreadable"), "an unreadable report left no trace:\n{log}");
}

#[tokio::test]
async fn text_ash_s_client_never_writes_never_reaches_ash_s_log() {
    // The report sits where a player can edit it, and ash's log is what a
    // player pastes into a support channel. Two ways text could ride from one
    // to the other: a field ash logs, and the parser's own error message,
    // which quotes a string it did not expect.
    let f = fixture();
    let id = f.modded().await;
    let log = || std::fs::read_to_string(f.ash.diagnostics().path()).unwrap_or_default();

    // One forgery per report, so each guard is tested on its own - with two
    // in one report, whichever guard ran first would hide the other's absence.
    for forged in [
        r#"{ "client": "0.1.0 FORGED-IN-THE-VERSION", "features": [
            { "id": "toggle-sprint", "name": "Toggle sprint", "status": "degraded" } ] }"#,
        r#"{ "client": "0.1.0", "features": [
            { "id": "toggle sprint FORGED-IN-AN-ID", "name": "Toggle sprint", "status": "degraded" } ] }"#,
        r#"{ "client": "0.1.0", "features": [
            { "id": "toggle-sprint", "name": "FORGED-IN-THE-NAME", "status": "degraded" } ] }"#,
        r#"{ "client": "0.1.0", "features": [ "FORGED-IN-AN-ERROR" ] }"#,
    ] {
        write_load_report(&f, &id, forged);
        f.ash.degradation_notice(&id).unwrap();
    }

    let written = log();
    assert!(!written.contains("FORGED"), "the report's own text reached ash's log:\n{written}");
    assert!(
        written.contains("load-report-unreadable"),
        "an unreadable report left no trace:\n{written}"
    );
}

#[tokio::test]
async fn reading_the_notice_puts_the_report_in_ash_s_log_once() {
    // Straight after a degraded session is when a player reads the notice
    // and opens the log - before they have launched anything again.
    let f = fixture();
    let id = f.modded().await;
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);

    f.ash.degradation_notice(&id).unwrap();
    f.ash.degradation_notice(&id).unwrap();

    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    let lines = log.lines().filter(|l| l.contains("toggle-sprint=degraded")).count();
    assert_eq!(lines, 1, "expected the report once:\n{log}");
}

#[tokio::test]
async fn a_report_from_before_the_last_launch_is_not_passed_off_as_that_session_s() {
    // The client writes a report as it starts. A report older than the last
    // launch means that session's client never wrote one - it crashed first,
    // or could not write - and the verdict it still shows is somebody else's.
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);
    let report = f.ash.game_directory(&id).join("ash").join("load-report.json");
    let an_hour_ago = std::time::SystemTime::now() - std::time::Duration::from_secs(3600);
    std::fs::File::options().write(true).open(&report).unwrap().set_modified(an_hour_ago).unwrap();

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launched");

    assert_eq!(
        f.ash.degradation_notice(&id).unwrap(),
        None,
        "a report from before the last launch was shown as the last session's"
    );
    let log = std::fs::read_to_string(f.ash.diagnostics().path()).expect("ash's log");
    assert!(log.contains("load-report-stale"), "the missing report left no trace:\n{log}");
}

#[tokio::test]
async fn a_report_from_a_newer_client_still_surfaces_what_degraded() {
    // The client ships inside the installer, so the two never skew in a
    // released build - but a player can run a newer instance's game
    // directory under an older launcher, and a status this launcher has
    // never heard of must not throw the whole report away.
    let f = fixture();
    let id = f.modded().await;
    write_load_report(
        &f,
        &id,
        r#"{ "client": "9.9.9", "written": "somewhen", "features": [
            { "id": "freelook", "name": "Freelook", "status": "partially-loaded" },
            { "id": "toggle-sprint", "name": "Toggle sprint", "status": "degraded" } ] }"#,
    );

    let notice = f.ash.degradation_notice(&id).unwrap().expect("the degraded feature was lost");
    assert_eq!(notice.features, ["Toggle sprint"]);
}

// ---- this instance at a glance ----------------------------------------------
//
// The Play page's middle card. The features that are on come from the load
// report and the client's settings together: the report names them and says
// what loaded, and the settings are newer when the player edits them between
// sessions. Neither file is ever written here.

/// The client's settings as it writes them on its first run, every switch on.
fn write_client_settings(f: &Fixture, id: &InstanceId, body: &str) {
    let path = f.ash.game_directory(id).join("config").join("ash.properties");
    std::fs::write(path, body).unwrap();
}

const EVERY_SWITCH_ON: &str = "# ash's settings\nfps-readout.enabled = true\n\
                               toggle-sprint.enabled = true\ncrosshair.enabled = true\n";

fn features_on(f: &Fixture, id: &InstanceId) -> AshFeatures {
    f.ash.instance_glance(id).expect("glance").features
}

#[tokio::test]
async fn an_ash_instance_says_nothing_of_its_features_until_the_client_has_run() {
    let f = fixture();
    let id = f.modded().await;

    assert_eq!(features_on(&f, &id), AshFeatures::NotReported);
}

#[tokio::test]
async fn the_features_on_are_those_that_loaded_and_are_switched_on() {
    let f = fixture();
    let id = f.modded().await;
    write_client_settings(&f, &id, EVERY_SWITCH_ON);
    write_load_report(&f, &id, TOGGLE_SPRINT_DEGRADED);

    // Toggle sprint is switched on, but did not load, so it is not running.
    assert_eq!(features_on(&f, &id), AshFeatures::On { features: vec!["FPS readout".into()] });
}

#[tokio::test]
async fn a_feature_switched_off_in_the_file_since_the_session_is_not_on() {
    let f = fixture();
    let id = f.modded().await;
    write_load_report(
        &f,
        &id,
        r#"{ "client": "0.1.0", "features": [
            { "id": "fps-readout", "name": "FPS readout", "status": "loaded" },
            { "id": "crosshair", "name": "Crosshair", "status": "off" } ] }"#,
    );
    write_client_settings(&f, &id, "fps-readout.enabled=false\ncrosshair.enabled: true\n");

    assert_eq!(features_on(&f, &id), AshFeatures::On { features: vec!["Crosshair".into()] });
}

#[tokio::test]
async fn a_feature_with_no_switch_is_not_listed_as_one_that_is_on() {
    let f = fixture();
    let id = f.modded().await;
    write_load_report(
        &f,
        &id,
        r#"{ "client": "0.1.0", "features": [
            { "id": "settings-screen", "name": "ash's settings screen", "status": "loaded" } ] }"#,
    );
    write_client_settings(&f, &id, EVERY_SWITCH_ON);

    assert_eq!(features_on(&f, &id), AshFeatures::On { features: vec![] });
}

#[tokio::test]
async fn a_switch_the_client_cannot_read_is_taken_as_the_report_has_it() {
    let f = fixture();
    let id = f.modded().await;
    write_load_report(
        &f,
        &id,
        r#"{ "client": "0.1.0", "features": [
            { "id": "fps-readout", "name": "FPS readout", "status": "loaded" },
            { "id": "crosshair", "name": "Crosshair", "status": "off" } ] }"#,
    );
    // The client reads neither, falls back to its defaults, and reports what
    // that came to. ash does not know the defaults and does not guess them.
    write_client_settings(&f, &id, "fps-readout.enabled = yes\ncrosshair.enabled = on\n");

    assert_eq!(features_on(&f, &id), AshFeatures::On { features: vec!["FPS readout".into()] });
}

#[tokio::test]
async fn a_vanilla_instance_has_no_ash_client_and_no_mods() {
    let f = fixture();
    f.modded().await;
    let plain = f.ash.create_instance("plain", VERSION, Loader::Vanilla).unwrap().id;
    // A vanilla game loads nothing from the folder, so this is not a mod it runs.
    std::fs::write(f.ash.game_directory(&plain).join("mods").join("sodium.jar"), b"").unwrap();

    let glance = f.ash.instance_glance(&plain).unwrap();

    assert_eq!(glance.features, AshFeatures::NoClient);
    assert!(glance.mods.is_empty(), "{:?}", glance.mods);
}

/// A jar that names itself the way a Fabric mod does.
fn fabric_mod(name: &str) -> Vec<u8> {
    use std::io::Write;
    use zip::write::SimpleFileOptions;

    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);
    writer.start_file("fabric.mod.json", options).unwrap();
    write!(writer, r#"{{"schemaVersion": 1, "id": "x", "name": "{name}"}}"#).unwrap();
    writer.finish().unwrap().into_inner()
}

#[tokio::test]
async fn a_players_mods_are_not_listed_while_they_do_not_load() {
    let f = fixture();
    let id = f.modded().await;
    f.prepare(&id).await;
    let mods = f.ash.game_directory(&id).join("mods");
    std::fs::write(mods.join("sodium-fabric-0.6.13+mc1.21.11.jar"), fabric_mod("Sodium")).unwrap();

    // The loader reads an empty folder of ash's instead, so listing Sodium
    // would be telling the player it runs when it does not.
    assert!(f.ash.instance_glance(&id).unwrap().mods.is_empty());
}

fn value_of<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| a == flag)?;
    args.get(at + 1).map(String::as_str)
}
