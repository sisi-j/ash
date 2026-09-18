//! Legacy Fabric on 1.8.9, which is where the merge gets tested properly
//! rather than merely exercised.
//!
//! Two things make this target different, and both are load-bearing. Its
//! profile omits the structured `arguments` block entirely, so the merge has
//! to compose with the pre-1.13 argument format the game still uses. And it
//! swaps in its own LWJGL 2 fork, which replaces the game's copy by naming
//! the same `group:artifact` at a different version - a replacement that has
//! to beat rules the vanilla entries carry, or the classpath ends up with
//! two LWJGL 2 jars and two sets of natives unpacked into one directory.
//!
//! Every artifact is built here and hashed from its own bytes, and the pins
//! come in through `Config` - the real ones name real jars.

use std::sync::{Arc, OnceLock};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{
    Ash, Cancel, Config, InstanceId, Loader, LoaderPin, NullSink, PinnedFile, PinnedLibrary,
    PinnedNative, VERSION_MANIFEST_URL,
};

mod common;

const VERSION: &str = "1.8.9";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/bb/1.8.9.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/dd/client.jar";
const CLIENT_JAR: &[u8] = b"pretend this is the 1.8.9 client jar";

/// Upstream Fabric's Maven, which serves the loader and its own libraries.
const FABRIC: &str = "https://fabric.test/";
/// Legacy Fabric's single self-hosted repository - the one with a bus factor
/// of one, and the reason the mirror exists.
const LEGACY: &str = "https://legacy.test/";
/// ash's mirror of everything [`LEGACY`] serves. Flat, like the real one.
const MIRROR: &str = "https://mirror.test/";

const DOCUMENT_URL: &str =
    "https://fabric.test/net/fabricmc/fabric-loader/9.9.3/fabric-loader-9.9.3.json";

const KNOT: &str = "net.fabricmc.loader.impl.launch.knot.KnotClient";

/// What 1.8.9 ships: **two** LWJGL 2 jars at the same `group:artifact`, one
/// carved out on macOS and one that is macOS-only, plus a platform jar whose
/// natives map is what says "unpack this".
///
/// Both matter. The fork has to displace the pair, and the macOS-only entry
/// is the one it exists for - taking Legacy Fabric's LWJGL instead of the
/// game's is what makes 1.8.9 run on Apple Silicon at all.
const GAME_LWJGL: &str = "org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209";
const GAME_LWJGL_OSX: &str = "org.lwjgl.lwjgl:lwjgl:2.9.2-nightly-20140822";
const GAME_LWJGL_OSX_URL: &str =
    "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.2-nightly-20140822/lwjgl-2.9.2-nightly-20140822.jar";
const GAME_PLATFORM: &str = "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209";
const GAME_LWJGL_URL: &str =
    "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar";
const GAME_PLATFORM_URL: &str =
    "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar";

/// Legacy Fabric's fork of the same coordinates.
const FORK_LWJGL: &str = "org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17";
const FORK_UTIL: &str = "org.lwjgl.lwjgl:lwjgl_util:2.9.4+legacyfabric.17";
const FORK_PLATFORM: &str = "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17";

const ASM: &str = "org.ow2.asm:asm:9.10.1";
const INTERMEDIARY: &str = "net.legacyfabric:intermediary:1.8.9";
const LOADER: &str = "net.fabricmc:fabric-loader:9.9.3";
const API: &str = "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:9.9.9+1.8.9";
/// One of the API's modules. The aggregator above is metadata only, so a
/// module is what actually carries the code ash's client calls - and there
/// being two bundled mods here rather than one is what stops a test passing
/// against an install that only ever handled the first.
const API_MODULE: &str =
    "net.legacyfabric.legacy-fabric-api:legacy-fabric-rendering-api-v1-common:9.9.9";

const ASM_JAR: &[u8] = b"pretend this is asm";
const INTERMEDIARY_JAR: &[u8] = b"pretend this is the legacy intermediary";
const LOADER_JAR: &[u8] = b"pretend this is fabric loader";
const API_JAR: &[u8] = b"pretend this is the legacy api aggregator";
const API_MODULE_JAR: &[u8] = b"pretend this is the rendering api module";

/// ash's own client for this target, as the installer leaves it on disk.
///
/// Never routed. This jar ships with the launcher and is never downloaded, so
/// a `FakeHttp` with no route for it is part of the proof.
const ASH_CLIENT: &str = "ash-client-1.8.9.jar";
const ASH_CLIENT_JAR: &[u8] = b"pretend this is ash's own 1.8.9 client";
const FORK_LWJGL_JAR: &[u8] = b"pretend this is the lwjgl fork";
const FORK_UTIL_JAR: &[u8] = b"pretend this is lwjgl_util";

/// The one file inside a native jar that proves which jar was unpacked.
const FORK_NATIVE_FILE: &str = "lwjgl64.dll";
const FORK_NATIVE_BODY: &[u8] = b"the fork's native library";
const GAME_NATIVE_FILE: &str = "lwjgl64-vanilla.dll";
const GAME_NATIVE_BODY: &[u8] = b"the game's own native library";

// ---- fixtures ---------------------------------------------------------------

fn maven_url(repository: &str, coordinate: &str) -> String {
    format!("{repository}{}", maven_path(coordinate))
}

fn maven_path(coordinate: &str) -> String {
    let mut parts = coordinate.split(':');
    let group = parts.next().expect("a group").replace('.', "/");
    let artifact = parts.next().expect("an artifact");
    let version = parts.next().expect("a version");
    let classifier = match parts.next() {
        Some(c) => format!("-{c}"),
        None => String::new(),
    };
    format!("{group}/{artifact}/{version}/{artifact}-{version}{classifier}.jar")
}

/// Where the mirror keeps one artifact.
///
/// Flat, and `+` becomes `-`, which is the rule ash's real mirror follows
/// because GitHub mangles some characters in release asset names.
/// The file name a coordinate's jar is installed under, worked out here
/// rather than asked of ash - if the two ever disagree, the assertion should
/// fail rather than follow along.
fn file_name(coordinate: &str) -> String {
    maven_path(coordinate).rsplit('/').next().expect("a file name").to_owned()
}

fn mirror_url(coordinate: &str) -> String {
    format!("{MIRROR}{}", file_name(coordinate).replace('+', "-"))
}

fn depot_relative(coordinate: &str) -> String {
    format!("libraries/{}", maven_path(coordinate))
}

/// A real jar, because preparation unpacks these and a fixture it could not
/// read would prove nothing.
fn native_jar(name: &str, body: &[u8]) -> Vec<u8> {
    use std::io::Write;
    use zip::write::SimpleFileOptions;

    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    // Stored, not deflated: ash's zip support is decompress-only.
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);

    // Excluded by the profile ash writes, so finding it unpacked would mean
    // the exclusion never reached preparation.
    writer.start_file("META-INF/MANIFEST.MF", options).expect("manifest entry");
    writer.write_all(b"Manifest-Version: 1.0\n").expect("manifest body");
    writer.start_file(name, options).expect("library entry");
    writer.write_all(body).expect("library body");

    writer.finish().expect("finish").into_inner()
}

fn fork_native() -> &'static [u8] {
    static JAR: OnceLock<Vec<u8>> = OnceLock::new();
    JAR.get_or_init(|| native_jar(FORK_NATIVE_FILE, FORK_NATIVE_BODY))
}

fn game_native() -> &'static [u8] {
    static JAR: OnceLock<Vec<u8>> = OnceLock::new();
    JAR.get_or_init(|| native_jar(GAME_NATIVE_FILE, GAME_NATIVE_BODY))
}

fn document() -> String {
    format!(
        r#"{{"version":2,"min_java_version":8,
          "libraries":{{
            "common":[
              {{"name":"{ASM}","url":"{FABRIC}","sha1":"{asm_sha}","size":{asm_size}}}
            ],
            "client":[]
          }},
          "mainClass":{{"client":"{KNOT}",
                        "server":"net.fabricmc.loader.impl.launch.knot.KnotServer"}}}}"#,
        asm_sha = common::sha1(ASM_JAR),
        asm_size = ASM_JAR.len(),
    )
}

fn pins() -> &'static [LoaderPin] {
    static PINS: OnceLock<&'static [LoaderPin]> = OnceLock::new();

    PINS.get_or_init(|| {
        let leak = |text: String| -> &'static str { Box::leak(text.into_boxed_str()) };
        // Legacy Fabric's own artifacts: mirrored, because there is one
        // copy of them in the world.
        let mirrored = |name: &'static str, jar: &'static [u8]| PinnedLibrary {
            name,
            repository: LEGACY,
            mirror: Some(leak(mirror_url(name))),
            sha1: leak(common::sha1(jar)),
            size: jar.len() as u64,
            natives: &[],
        };
        // Upstream Fabric's: not mirrored, and so a control for every test
        // below that blocks Legacy Fabric's host.
        let upstream = |name: &'static str, jar: &'static [u8]| PinnedLibrary {
            name,
            repository: FABRIC,
            mirror: None,
            sha1: leak(common::sha1(jar)),
            size: jar.len() as u64,
            natives: &[],
        };

        let document = document();
        let natives: &'static [PinnedNative] = Box::leak(Box::new([PinnedNative {
            mirror: Some(leak(mirror_url(&format!("{FORK_PLATFORM}:natives-windows")))),
            os: ash_core::Os::Windows,
            classifier: "natives-windows",
            sha1: leak(common::sha1(fork_native())),
            size: fork_native().len() as u64,
        }]));

        let libraries: &'static [PinnedLibrary] = Box::leak(Box::new([
            mirrored(INTERMEDIARY, INTERMEDIARY_JAR),
            upstream(LOADER, LOADER_JAR),
            mirrored(FORK_LWJGL, FORK_LWJGL_JAR),
            mirrored(FORK_UTIL, FORK_UTIL_JAR),
            // The coordinate names no jar of its own; only the platform jars
            // below, exactly as Mojang's own entry for it is shaped.
            PinnedLibrary {
                name: FORK_PLATFORM,
                repository: LEGACY,
                mirror: None,
                sha1: "",
                size: 0,
                natives,
            },
        ]));
        let bundled: &'static [PinnedLibrary] =
            Box::leak(Box::new([mirrored(API, API_JAR), mirrored(API_MODULE, API_MODULE_JAR)]));

        let pins: &'static [LoaderPin] = Box::leak(Box::new([LoaderPin {
            loader: Loader::LegacyFabric,
            version_id: VERSION,
            loader_version: "9.9.3",
            document: PinnedFile {
                url: DOCUMENT_URL,
                sha1: leak(common::sha1(document.as_bytes())),
                size: document.len() as u64,
            },
            libraries,
            // Legacy Fabric emits no argument block at all.
            jvm_arguments: &[],
            client_jar: Some(ASH_CLIENT),
            bundled_mods: bundled,
        }]));
        pins
    })
}

/// 1.8.9 as Mojang publishes it: `minecraftArguments`, no `arguments`, and
/// LWJGL 2 with a natives map.
fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "minecraftArguments":"--username ${{auth_player_name}} --version {VERSION}",
        "javaVersion":{{"component":"jre-legacy","majorVersion":8}},
        "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},
                      "url":"{CLIENT_URL}"}}}},
        "libraries":[
          {{"name":"{GAME_LWJGL}",
            "rules":[{{"action":"allow"}},{{"action":"disallow","os":{{"name":"osx"}}}}],
            "downloads":{{"artifact":{{
              "path":"org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
              "sha1":"{game_lwjgl_sha}","size":{game_lwjgl_size},"url":"{GAME_LWJGL_URL}"}}}}}},
          {{"name":"{GAME_LWJGL_OSX}",
            "rules":[{{"action":"allow","os":{{"name":"osx"}}}}],
            "downloads":{{"artifact":{{
              "path":"org/lwjgl/lwjgl/lwjgl/2.9.2-nightly-20140822/lwjgl-2.9.2-nightly-20140822.jar",
              "sha1":"{game_lwjgl_sha}","size":{game_lwjgl_size},"url":"{GAME_LWJGL_OSX_URL}"}}}}}},
          {{"name":"{GAME_PLATFORM}",
            "natives":{{"windows":"natives-windows"}},
            "extract":{{"exclude":["META-INF/"]}},
            "downloads":{{"classifiers":{{"natives-windows":{{
              "path":"org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar",
              "sha1":"{game_native_sha}","size":{game_native_size},
              "url":"{GAME_PLATFORM_URL}"}}}}}}}}
        ]}}"#,
        client_sha = common::sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        game_lwjgl_sha = common::sha1(b"pretend this is the game's lwjgl"),
        game_lwjgl_size = b"pretend this is the game's lwjgl".len(),
        game_native_sha = common::sha1(game_native()),
        game_native_size = game_native().len(),
    )
}

fn manifest_json() -> String {
    format!(
        r#"{{"latest":{{"release":"{VERSION}","snapshot":"{VERSION}"}},"versions":[
            {{"id":"{VERSION}","type":"release","releaseTime":"2015-12-09T09:00:00+00:00",
              "url":"{VERSION_URL}","sha1":"{}"}}
        ]}}"#,
        common::sha1(version_json().as_bytes())
    )
}

/// Everything a modded 1.8.9 needs.
///
/// The game's own LWJGL is deliberately **not** routed: the fork replaces it,
/// so asking for it means the replacement failed, and an unrouted URL makes
/// `FakeHttp` panic rather than let that pass. The one vanilla test adds it.
fn serving() -> Arc<FakeHttp> {
    common::with_runtime_routes(common::with_auth_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(VERSION_URL, HttpResponse::ok(version_json()))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR))
            .route(DOCUMENT_URL, HttpResponse::ok(document()))
            .route(maven_url(FABRIC, ASM), HttpResponse::ok(ASM_JAR))
            .route(maven_url(FABRIC, LOADER), HttpResponse::ok(LOADER_JAR))
            .route(maven_url(LEGACY, INTERMEDIARY), HttpResponse::ok(INTERMEDIARY_JAR))
            .route(maven_url(LEGACY, FORK_LWJGL), HttpResponse::ok(FORK_LWJGL_JAR))
            .route(maven_url(LEGACY, FORK_UTIL), HttpResponse::ok(FORK_UTIL_JAR))
            .route(
                maven_url(LEGACY, &format!("{FORK_PLATFORM}:natives-windows")),
                HttpResponse::ok(fork_native().to_vec()),
            )
            .route(maven_url(LEGACY, API), HttpResponse::ok(API_JAR))
            .route(maven_url(LEGACY, API_MODULE), HttpResponse::ok(API_MODULE_JAR)),
    ))
}

/// The same bytes, from ash's mirror.
///
/// Routed alongside upstream rather than instead of it, so every test that
/// does not block a host proves ash still prefers the original.
fn with_mirror(http: Arc<FakeHttp>) -> Arc<FakeHttp> {
    http.route(mirror_url(INTERMEDIARY), HttpResponse::ok(INTERMEDIARY_JAR))
        .route(mirror_url(FORK_LWJGL), HttpResponse::ok(FORK_LWJGL_JAR))
        .route(mirror_url(FORK_UTIL), HttpResponse::ok(FORK_UTIL_JAR))
        .route(
            mirror_url(&format!("{FORK_PLATFORM}:natives-windows")),
            HttpResponse::ok(fork_native().to_vec()),
        )
        .route(mirror_url(API), HttpResponse::ok(API_JAR))
        .route(mirror_url(API_MODULE), HttpResponse::ok(API_MODULE_JAR))
}

fn with_the_games_lwjgl(http: Arc<FakeHttp>) -> Arc<FakeHttp> {
    http.route(GAME_LWJGL_URL, HttpResponse::ok(b"pretend this is the game's lwjgl".to_vec()))
        .route(GAME_LWJGL_OSX_URL, HttpResponse::ok(b"pretend this is the game's lwjgl".to_vec()))
        .route(GAME_PLATFORM_URL, HttpResponse::ok(game_native().to_vec()))
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    tmp: tempfile::TempDir,
}

/// Put ash's own client where an installed ash would have it. The
/// installer's job in real life, and the fixture's here.
fn install_ash_client(client_root: &std::path::Path) {
    std::fs::create_dir_all(client_root).expect("the client root");
    std::fs::write(client_root.join(ASH_CLIENT), ASH_CLIENT_JAR).expect("ash's client");
}

fn fixture_with(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let config = Config { loaders: pins(), ..Config::rooted_at(tmp.path()) };
    install_ash_client(&config.client_root);
    let ash = Ash::new(
        config,
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
        self.ash.create_instance("legacy", VERSION, Loader::LegacyFabric).expect("instance").id
    }
}

fn value_of<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| a == flag)?;
    args.get(at + 1).map(String::as_str)
}

// ---- the LWJGL replacement ---------------------------------------------------

#[tokio::test]
async fn the_classpath_carries_exactly_one_lwjgl_and_it_is_the_fork() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");
    let classpath = value_of(&view.args, "-cp").expect("a classpath");

    // Counted on the whole string rather than by splitting it. A classpath
    // is separated by `;` on Windows and `:` on macOS, and its paths contain
    // both, so splitting on either is a trap - and on the platform where the
    // split produced one entry this assertion would pass without proving
    // anything. `lwjgl-2` matches only the jar this is about: `lwjgl_util-2`
    // and `lwjgl-platform-2` do not contain it.
    assert_eq!(
        classpath.matches("lwjgl-2").count(),
        1,
        "expected exactly one lwjgl on the classpath: {classpath}"
    );
    assert!(
        classpath.contains("lwjgl-2.9.4+legacyfabric.17.jar"),
        "the fork is not on the classpath: {classpath}"
    );
    // Neither of the game's own copies survives. Two LWJGL 2 jars on one
    // classpath is a game that starts or does not depending on which the JVM
    // reaches first.
    //
    // Worth being precise about what this proves: on a Windows runner the
    // macOS-only entry is excluded by its own rule as well as by the
    // replacement, so this assertion alone cannot tell the two apart. The
    // platform-independent proof that the *pair* is displaced - on Windows
    // and on macOS, before any rule is evaluated - is
    // `profile::tests::a_rule_on_the_parents_entry_does_not_save_it_from_replacement`.
    for dropped in ["2.9.4-nightly-20150209", "2.9.2-nightly-20140822"] {
        assert!(!classpath.contains(dropped), "the game's {dropped} lwjgl survived: {classpath}");
    }
}

#[tokio::test]
async fn a_rule_on_the_games_entry_does_not_save_it_from_replacement() {
    let f = fixture();
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    // The game's LWJGL carries `allow` + `disallow osx`; the fork carries no
    // rules at all. Replacing before rules are evaluated is what lets a
    // rule-less entry displace a rule-bearing one - and `serving()` routes
    // no URL for the game's copy, so fetching it would have panicked.
    let requested = f.http.requested();
    assert!(!requested.is_empty(), "the test proves nothing if nothing was requested");
    for dropped in ["2.9.4-nightly-20150209", "2.9.2-nightly-20140822"] {
        assert!(
            !requested.iter().any(|url| url.contains(dropped)),
            "ash fetched the {dropped} lwjgl the fork replaces"
        );
    }
}

#[tokio::test]
async fn the_forks_natives_are_the_ones_unpacked() {
    let f = fixture();
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    // A merged profile is its own version id, so its natives are unpacked
    // beside rather than over a vanilla 1.8.9 instance's - the two genuinely
    // differ, which is the whole reason the fork exists.
    let natives = f.tmp.path().join("depot/versions/fabric-loader-9.9.3-1.8.9/natives");
    assert!(
        natives.join(FORK_NATIVE_FILE).is_file(),
        "the fork's native was not unpacked: {:?}",
        std::fs::read_dir(&natives).map(|d| d.flatten().map(|e| e.file_name()).collect::<Vec<_>>())
    );
    assert!(!natives.join(GAME_NATIVE_FILE).exists(), "the game's native was unpacked too");
    // The exclusion ash writes into the profile has to reach preparation.
    assert!(!natives.join("META-INF").exists(), "META-INF was unpacked");
}

// ---- the pre-1.13 argument format --------------------------------------------

#[tokio::test]
async fn the_merge_composes_with_the_pre_1_13_argument_format() {
    let f = fixture();
    let id = f.modded().await;

    let view = f.ash.preview_launch(&id, &NullSink, &Cancel::new()).await.expect("previewed");

    // Legacy Fabric contributes no argument block, so the merged document
    // has empty structured lists and 1.8.9's own `minecraftArguments` is
    // still what has to reach the command line.
    assert_eq!(
        value_of(&view.args, "--username"),
        Some(common::PLAYER_NAME),
        "the pre-1.13 arguments were lost: {:?}",
        view.args
    );
    assert!(view.args.contains(&"--version".to_owned()));

    // And the two facts the structured list replaced in 1.13, which no
    // pre-1.13 version states, are still synthesised.
    assert!(view.args.iter().any(|a| a.starts_with("-Djava.library.path=")));
    assert!(view.args.contains(&"-cp".to_owned()));

    // The loader still starts, rather than the game.
    assert!(view.args.contains(&KNOT.to_owned()));
    // Legacy Fabric deliberately emits no `-DFabricMcEmu`.
    assert!(
        !view.args.iter().any(|a| a.starts_with("-DFabricMcEmu")),
        "an argument Legacy Fabric does not emit reached the command line"
    );
}

// ---- the same terms as the modern target --------------------------------------

#[tokio::test]
async fn loader_artifacts_are_verified_exactly_as_on_the_modern_target() {
    let http = serving().route_sequence(
        maven_url(LEGACY, INTERMEDIARY),
        vec![
            HttpResponse::ok(b"not the intermediary".to_vec()),
            HttpResponse::ok(b"still not it".to_vec()),
        ],
    );
    let f = fixture_with(http);
    let id = f.modded().await;

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("a corrupt intermediary is refused");

    assert_eq!(err.kind(), "verification_failed");
}

#[tokio::test]
async fn a_second_legacy_instance_needs_nothing_new() {
    let f = fixture();
    let first = f.modded().await;
    f.ash.prepare_instance(&first, &NullSink, &Cancel::new()).await.expect("prepared");

    let second =
        f.ash.create_instance("another", VERSION, Loader::LegacyFabric).expect("instance").id;
    let plan = f.ash.plan_instance(&second).await.expect("planned");

    assert!(plan.total_files > 0, "the test proves nothing if the plan is empty");
    assert_eq!(plan.missing_files, 0, "loader artifacts are shared through the depot");
}

#[tokio::test]
async fn a_prepared_legacy_instance_plans_with_no_network() {
    let f = fixture();
    let id = f.modded().await;
    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    let offline = Ash::new(
        Config { loaders: pins(), ..Config::rooted_at(f.tmp.path()) },
        FakeHttp::offline() as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    );

    let plan = offline.plan_instance(&id).await.expect("a prepared instance plans offline");

    assert!(plan.total_files > 0, "the test proves nothing if the plan is empty");
    assert_eq!(plan.missing_files, 0);
    assert_eq!(plan.version_id, "fabric-loader-9.9.3-1.8.9");
}

#[tokio::test]
async fn the_api_lands_where_the_loader_looks() {
    let f = fixture();
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    assert!(f
        .ash
        .game_directory(&id)
        .join("mods")
        .join("legacy-fabric-api-9.9.9+1.8.9.jar")
        .is_file());
}

// ---- and vanilla 1.8.9 is untouched -------------------------------------------

#[tokio::test]
async fn a_vanilla_1_8_9_instance_still_launches_unchanged() {
    let f = fixture_with(with_the_games_lwjgl(serving()));
    f.ash.begin_sign_in().await.expect("device code");
    f.ash.poll_sign_in().await.expect("sign-in");
    let plain = f.ash.create_instance("plain", VERSION, Loader::Vanilla).expect("instance").id;

    let view = f.ash.preview_launch(&plain, &NullSink, &Cancel::new()).await.expect("previewed");
    let classpath = value_of(&view.args, "-cp").expect("a classpath");

    assert!(view.args.contains(&"net.minecraft.client.main.Main".to_owned()));
    assert_eq!(value_of(&view.args, "--username"), Some(common::PLAYER_NAME));
    // The game's own LWJGL, not the fork's - nothing about a vanilla
    // instance changes because a loader exists.
    assert!(classpath.contains("2.9.4-nightly-20150209"), "{classpath}");
    assert!(!classpath.contains("legacyfabric"), "{classpath}");
    // And its natives are its own, in its own directory.
    assert!(f.tmp.path().join("depot/versions/1.8.9/natives").join(GAME_NATIVE_FILE).is_file());
}

#[test]
fn both_version_targets_offer_the_loader_ash_pinned_for_them() {
    let f = fixture();

    assert_eq!(f.ash.loaders_for(VERSION), [Loader::Vanilla, Loader::LegacyFabric]);

    // Fabric proper is not on offer here: what 1.8.9 runs is Legacy Fabric,
    // and an instance pairing the two could never launch.
    let err = f
        .ash
        .create_instance("wrong", VERSION, Loader::Fabric)
        .expect_err("an unpinned pairing is refused");

    assert_eq!(err.kind(), "loader_unavailable");
    assert!(f.ash.instances().unwrap().is_empty(), "nothing was created");
    let message = err.user_message();
    assert!(message.contains(VERSION), "{message}");
    assert!(!message.contains('/'), "leaked a path or url: {message}");
}

// ---- the mirror ---------------------------------------------------------------

#[tokio::test]
async fn the_mirror_is_left_alone_while_the_original_answers() {
    let f = fixture_with(with_mirror(serving()));
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    let requested = f.http.requested();
    // Upstream stays the source of truth while it is up. Without this the
    // test below could pass on a mirror ash was using all along.
    assert!(
        requested.iter().any(|url| url.starts_with(LEGACY)),
        "the test proves nothing if upstream was never asked"
    );
    assert!(
        !requested.iter().any(|url| url.starts_with(MIRROR)),
        "ash went to the mirror while the original was answering"
    );
}

#[tokio::test]
async fn an_instance_prepares_and_launches_with_legacy_fabrics_repository_unreachable() {
    // Blocked, not argued. The whole host is gone, which is what a single
    // self-hosted repository with no published mirror looks like the day it
    // goes away - not one URL failing.
    let f = fixture_with(with_mirror(serving()).host_unreachable(LEGACY));
    let id = f.modded().await;

    let view = f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launches");

    // Everything that repository serves is in the depot anyway - the API's
    // modules included, which is most of what a 1.8.9 instance now needs from
    // it and all of what ash's own client calls into.
    for coordinate in [INTERMEDIARY, FORK_LWJGL, FORK_UTIL, API, API_MODULE] {
        assert!(
            f.tmp.path().join("depot").join(depot_relative(coordinate)).is_file(),
            "{coordinate} was not fetched from the mirror"
        );
    }
    // Including the natives, which had to be unpacked from a mirrored jar.
    let natives = f.tmp.path().join("depot/versions/fabric-loader-9.9.3-1.8.9/natives");
    assert!(natives.join(FORK_NATIVE_FILE).is_file(), "the fork's natives are missing");
    // And the game still starts through the loader.
    assert!(view.args.contains(&KNOT.to_owned()));

    let requested = f.http.requested();
    assert!(
        requested.iter().any(|url| url.starts_with(MIRROR)),
        "the test proves nothing if the mirror was never asked"
    );
    // Upstream Fabric is a different host and is not mirrored, so a fetch
    // from it here is the control: blocking one repository must not have
    // quietly rerouted everything.
    assert!(requested.iter().any(|url| url.starts_with(FABRIC)));
}

#[tokio::test]
async fn a_corrupt_original_is_not_papered_over_by_the_mirror() {
    // The mirror answers only when the original cannot be reached. An
    // artifact that *arrives* and fails its hash is a different thing
    // entirely, and quietly taking a good copy from somewhere else would
    // turn a tampered-with upstream into a silent success - which is exactly
    // the property that lets ash trust a second source at all.
    let http = with_mirror(serving()).route_sequence(
        maven_url(LEGACY, INTERMEDIARY),
        vec![
            HttpResponse::ok(b"not the intermediary".to_vec()),
            HttpResponse::ok(b"still not it".to_vec()),
        ],
    );
    let f = fixture_with(http);
    let id = f.modded().await;

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("a corrupt original is refused rather than replaced");

    assert_eq!(err.kind(), "verification_failed");
    // The mirror has a perfectly good copy and was deliberately not asked.
    assert!(
        !f.http.requested().iter().any(|url| url == &mirror_url(INTERMEDIARY)),
        "ash fell back to the mirror on a hash mismatch"
    );
}

#[tokio::test]
async fn a_mirrored_artifact_that_does_not_match_its_hash_is_rejected() {
    // The mirror is a second copy, not a second authority: it is verified
    // against the same pinned hash, so a mirror that had been tampered with
    // - or had simply drifted - cannot put anything into the depot.
    let http = with_mirror(serving())
        .host_unreachable(LEGACY)
        .route(mirror_url(INTERMEDIARY), HttpResponse::ok(b"not the intermediary".to_vec()));
    let f = fixture_with(http);
    let id = f.modded().await;

    let err = f
        .ash
        .prepare_instance(&id, &NullSink, &Cancel::new())
        .await
        .expect_err("a mirrored artifact that does not match is refused");

    assert_eq!(err.kind(), "verification_failed");
    assert!(
        !f.tmp.path().join("depot").join(depot_relative(INTERMEDIARY)).exists(),
        "the bad bytes reached the depot"
    );
}

// ---- ash's own client -------------------------------------------------------

#[tokio::test]
async fn ashs_own_client_and_everything_it_calls_land_in_the_instance() {
    let f = fixture_with(serving());
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    let mods = f.ash.game_directory(&id).join("mods");
    assert_eq!(
        std::fs::read(mods.join(ASH_CLIENT)).ok().as_deref(),
        Some(ASH_CLIENT_JAR),
        "ash's client is not in the instance, or is not the one that shipped"
    );
    // And the API beside it. The aggregator alone would put "Legacy Fabric
    // API" in the mod list and nothing on the class path: it is four entries
    // and no classes, so the module is what ash's client actually calls into.
    // A loader that cannot resolve a declared dependency refuses to start.
    assert!(mods.join(file_name(API)).is_file(), "the API aggregator is missing");
    assert!(mods.join(file_name(API_MODULE)).is_file(), "the API module is missing");

    // None of it came over the network. `serving` has no route for ash's own
    // client, so a fetch would have panicked rather than failed quietly.
    assert!(
        !f.http.requested().iter().any(|url| url.contains("ash-client")),
        "ash's own client was fetched; it ships in the installer"
    );
}

#[tokio::test]
async fn the_client_survives_legacy_fabrics_repository_being_unreachable() {
    // The client itself never depended on that host - it ships in the
    // installer - but everything it calls into does, and a client whose API
    // is missing is a game that will not start rather than a game without a
    // marker.
    let f = fixture_with(with_mirror(serving()).host_unreachable(LEGACY));
    let id = f.modded().await;

    f.ash.prepare_instance(&id, &NullSink, &Cancel::new()).await.expect("prepared");

    let mods = f.ash.game_directory(&id).join("mods");
    assert!(mods.join(ASH_CLIENT).is_file(), "ash's client is missing");
    assert!(mods.join(file_name(API_MODULE)).is_file(), "the API module is missing");
    assert!(
        f.http.requested().iter().any(|url| url.starts_with(MIRROR)),
        "the test proves nothing if the mirror was never asked"
    );
}
