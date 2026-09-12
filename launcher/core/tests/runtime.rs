//! Java runtime provisioning: selection, verification, and never touching
//! whatever Java happens to be on the machine.

use std::sync::{Arc, Mutex};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::{Ash, Cancel, Config, PrepareEvent, ProgressSink, VERSION_MANIFEST_URL};
use sha1::{Digest, Sha1};

mod common;

const MODERN_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.4.json";
const LEGACY_URL: &str = "https://piston-meta.mojang.com/v1/packages/bb/1.8.9.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const CLIENT_JAR: &[u8] = b"client jar";

fn sha1(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// A version with no libraries and no assets, so these tests exercise only
/// the runtime half of preparation.
fn version_json(id: &str, java: Option<&str>) -> String {
    let java_field = match java {
        Some(component) => {
            format!(r#""javaVersion":{{"component":"{component}","majorVersion":21}},"#)
        }
        // 1.8-era metadata predates the field entirely.
        None => String::new(),
    };
    format!(
        r#"{{"id":"{id}",{java_field}
            "downloads":{{"client":{{"sha1":"{}","size":{},"url":"{CLIENT_URL}"}}}},
            "libraries":[]}}"#,
        sha1(CLIENT_JAR),
        CLIENT_JAR.len()
    )
}

fn manifest_json() -> String {
    let modern = version_json("1.21.4", Some("java-runtime-delta"));
    let legacy = version_json("1.8.9", None);
    format!(
        r#"{{"latest":{{"release":"1.21.4","snapshot":"1.21.4"}},"versions":[
            {{"id":"1.21.4","type":"release","releaseTime":"2025-12-03T09:00:00+00:00",
              "url":"{MODERN_URL}","sha1":"{}"}},
            {{"id":"1.8.9","type":"release","releaseTime":"2015-12-09T11:00:00+00:00",
              "url":"{LEGACY_URL}","sha1":"{}"}}
        ]}}"#,
        sha1(modern.as_bytes()),
        sha1(legacy.as_bytes())
    )
}

fn serving() -> Arc<FakeHttp> {
    common::with_runtime_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(MODERN_URL, HttpResponse::ok(version_json("1.21.4", Some("java-runtime-delta"))))
            .route(LEGACY_URL, HttpResponse::ok(version_json("1.8.9", None)))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR)),
    )
}

#[derive(Default)]
struct Recorder(Mutex<Vec<PrepareEvent>>);

impl Recorder {
    fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }
    fn events(&self) -> Vec<PrepareEvent> {
        self.0.lock().unwrap().clone()
    }
}

impl ProgressSink for Recorder {
    fn emit(&self, event: PrepareEvent) {
        self.0.lock().unwrap().push(event);
    }
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    tmp: tempfile::TempDir,
}

fn fixture_with(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        Arc::clone(&http) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        "test-client",
    );
    Fixture { ash, http, tmp }
}

fn fixture() -> Fixture {
    fixture_with(serving())
}

// ---- selection -------------------------------------------------------------

#[tokio::test]
async fn a_modern_version_gets_the_modern_runtime() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();

    let runtime =
        f.ash.ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();

    assert_eq!(runtime.component, "java-runtime-delta");
    assert_eq!(runtime.version_name, "21.0.3");
}

#[tokio::test]
async fn a_version_whose_metadata_predates_the_field_gets_the_legacy_runtime() {
    let f = fixture();
    let instance = f.ash.create_instance("classic", "1.8.9").unwrap();

    let runtime =
        f.ash.ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();

    // Defaulting to a modern runtime would hand Java 21 to a game that
    // cannot run on it.
    assert_eq!(runtime.component, "jre-legacy");
    assert_eq!(runtime.version_name, "8.0.412");
}

#[tokio::test]
async fn the_two_first_class_targets_resolve_to_different_runtimes() {
    let f = fixture();
    let modern = f.ash.create_instance("modern", "1.21.4").unwrap();
    let legacy = f.ash.create_instance("classic", "1.8.9").unwrap();

    let a = f.ash.ensure_runtime(&modern.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();
    let b = f.ash.ensure_runtime(&legacy.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();

    assert_ne!(a.component, b.component);
    assert_ne!(a.java_executable, b.java_executable);
}

// ---- system java -----------------------------------------------------------

#[tokio::test]
async fn the_java_it_hands_back_is_always_inside_the_depot() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();

    let runtime =
        f.ash.ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();

    // Whatever is on PATH or in JAVA_HOME is almost certainly the wrong major
    // version, so ash never looks. The proof is that the path it returns is
    // one ash downloaded itself.
    let depot = f.tmp.path().join("depot");
    assert!(
        runtime.java_executable.starts_with(&depot),
        "{} is outside the depot",
        runtime.java_executable.display()
    );
    assert!(runtime.java_executable.is_file(), "the java binary was not downloaded");
}

#[tokio::test]
async fn runtime_files_land_under_a_platform_and_component_scoped_path() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();
    f.ash.ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();

    let runtimes = f.tmp.path().join("depot/runtimes");
    assert!(runtimes.is_dir());

    // Scoped by platform as well as component, so a depot copied between
    // machines can never serve the wrong architecture's binaries.
    let platform_dirs: Vec<_> = std::fs::read_dir(&runtimes).unwrap().flatten().collect();
    assert_eq!(platform_dirs.len(), 1);
    assert!(platform_dirs[0].path().join("java-runtime-delta/bin/java.exe").is_file());
}

// ---- verification and reuse ------------------------------------------------

#[tokio::test]
async fn runtime_files_are_verified_like_any_other_artifact() {
    let f = fixture_with(
        serving().route(common::JAVA_21_URL, HttpResponse::ok(b"not the real binary".to_vec())),
    );
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();

    let err = f
        .ash
        .ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("a corrupt java binary cannot be accepted");

    assert_eq!(err.kind(), "verification_failed");
}

#[tokio::test]
async fn a_tampered_runtime_manifest_is_rejected() {
    let f = fixture_with(
        serving().route(common::MODERN_MANIFEST_URL, HttpResponse::ok(r#"{"files":{}}"#)),
    );
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();

    let err = f
        .ash
        .ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("the manifest decides what every file below it is");

    assert_eq!(err.kind(), "verification_failed");
}

#[tokio::test]
async fn a_second_instance_on_the_same_runtime_downloads_nothing() {
    let f = fixture();
    let first = f.ash.create_instance("one", "1.21.4").unwrap();
    let second = f.ash.create_instance("two", "1.21.4").unwrap();

    f.ash.ensure_runtime(&first.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();
    let before = f.http.hits(common::JAVA_21_URL);

    let sink = Recorder::new();
    f.ash.ensure_runtime(&second.id, sink.as_ref(), &Cancel::new()).await.unwrap();

    assert_eq!(f.http.hits(common::JAVA_21_URL), before, "the runtime was fetched twice");
    assert!(sink.events().iter().any(|e| matches!(
        e,
        PrepareEvent::Planned { missing_files: 0, .. }
    )));
}

#[tokio::test]
async fn a_platform_mojang_does_not_publish_for_says_so() {
    // An index that knows the platform but not this component.
    let stripped = common::runtime_index().replace(r#""java-runtime-delta""#, r#""unused""#);
    let f = fixture_with(
        serving().route(common::RUNTIME_INDEX_URL, HttpResponse::ok(stripped)),
    );
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();

    let err = f
        .ash
        .ensure_runtime(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("no such runtime");

    assert_eq!(err.kind(), "runtime_unavailable");
    let message = err.user_message();
    assert!(!message.contains("https://"), "leaked a URL: {message}");
}

#[tokio::test]
async fn cancelling_stops_runtime_provisioning() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();
    let cancel = Cancel::new();
    cancel.cancel();

    let err = f
        .ash
        .ensure_runtime(&instance.id, &ash_core::NullSink, &cancel)
        .await
        .expect_err("cancelled");

    assert_eq!(err.kind(), "cancelled");
    assert_eq!(f.http.hits(common::JAVA_21_URL), 0);
}

// ---- preparation ties them together ----------------------------------------

#[tokio::test]
async fn preparing_an_instance_leaves_it_with_both_game_files_and_a_runtime() {
    let f = fixture();
    let instance = f.ash.create_instance("modern", "1.21.4").unwrap();
    let sink = Recorder::new();

    f.ash.prepare_instance(&instance.id, sink.as_ref(), &Cancel::new()).await.unwrap();

    let depot = f.tmp.path().join("depot");
    assert!(depot.join("versions/1.21.4/1.21.4.jar").is_file(), "game files");
    assert!(
        depot.join(format!(
            "runtimes/{}/java-runtime-delta/bin/java.exe",
            std::fs::read_dir(depot.join("runtimes"))
                .unwrap()
                .flatten()
                .next()
                .unwrap()
                .file_name()
                .to_string_lossy()
        ))
        .is_file(),
        "a runtime"
    );

    // An instance with every game file and no JRE is not prepared.
    let events = sink.events();
    let runtime_at = events.iter().position(|e| matches!(e, PrepareEvent::Runtime { .. }));
    let done_at = events.iter().position(|e| matches!(e, PrepareEvent::Done { .. }));
    assert!(runtime_at < done_at, "Done must come after the runtime is in place");
}
