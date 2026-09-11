//! Preparation: planning, verified download, resume, dedup and cancellation.
//!
//! Fixtures are built here rather than checked in, because every artifact
//! carries a hash ash will check - a stale fixture and a stale hash would
//! drift apart silently.

use std::fs;
use std::sync::{Arc, Mutex};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::{Ash, Cancel, Config, PrepareEvent, ProgressSink, VERSION_MANIFEST_URL};
use sha1::{Digest, Sha1};

const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.4.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/bb/client.jar";
const ASSET_INDEX_URL: &str = "https://piston-meta.mojang.com/v1/packages/cc/24.json";
const WIN_LIB_URL: &str = "https://libraries.minecraft.net/org/lwjgl/lwjgl-windows.jar";
const MAC_LIB_URL: &str = "https://libraries.minecraft.net/org/lwjgl/lwjgl-macos.jar";

const CLIENT_JAR: &[u8] = b"pretend this is a 25MB client jar";
const WIN_LIB: &[u8] = b"windows native library";
const MAC_LIB: &[u8] = b"macos native library";
const ASSET_A: &[u8] = b"asset one";
const ASSET_B: &[u8] = b"asset two";

fn sha1(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

fn asset_url(bytes: &[u8]) -> String {
    let h = sha1(bytes);
    format!("https://resources.download.minecraft.net/{}/{}", &h[..2], h)
}

fn asset_index_json() -> String {
    format!(
        r#"{{"objects":{{
            "minecraft/sounds/a.ogg":{{"hash":"{}","size":{}}},
            "minecraft/sounds/b.ogg":{{"hash":"{}","size":{}}}
        }}}}"#,
        sha1(ASSET_A),
        ASSET_A.len(),
        sha1(ASSET_B),
        ASSET_B.len()
    )
}

fn version_json() -> String {
    let index = asset_index_json();
    format!(
        r#"{{
          "id":"1.21.4",
          "mainClass":"net.minecraft.client.main.Main",
          "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
          "assetIndex":{{"id":"24","sha1":"{index_sha}","size":{index_size},"url":"{ASSET_INDEX_URL}"}},
          "downloads":{{"client":{{"sha1":"{client_sha}","size":{client_size},"url":"{CLIENT_URL}"}}}},
          "libraries":[
            {{"name":"org.lwjgl:lwjgl:windows",
              "rules":[{{"action":"allow","os":{{"name":"windows"}}}}],
              "downloads":{{"artifact":{{"path":"org/lwjgl/lwjgl-windows.jar","sha1":"{win_sha}","size":{win_size},"url":"{WIN_LIB_URL}"}}}}}},
            {{"name":"org.lwjgl:lwjgl:macos",
              "rules":[{{"action":"allow","os":{{"name":"osx"}}}}],
              "downloads":{{"artifact":{{"path":"org/lwjgl/lwjgl-macos.jar","sha1":"{mac_sha}","size":{mac_size},"url":"{MAC_LIB_URL}"}}}}}}
          ]
        }}"#,
        index_sha = sha1(index.as_bytes()),
        index_size = index.len(),
        client_sha = sha1(CLIENT_JAR),
        client_size = CLIENT_JAR.len(),
        win_sha = sha1(WIN_LIB),
        win_size = WIN_LIB.len(),
        mac_sha = sha1(MAC_LIB),
        mac_size = MAC_LIB.len(),
    )
}

fn manifest_json() -> String {
    let version = version_json();
    format!(
        r#"{{"latest":{{"release":"1.21.4","snapshot":"1.21.4"}},
            "versions":[{{"id":"1.21.4","type":"release",
              "releaseTime":"2025-12-03T09:00:00+00:00",
              "url":"{VERSION_URL}","sha1":"{}"}}]}}"#,
        sha1(version.as_bytes())
    )
}

fn serving() -> Arc<FakeHttp> {
    let index = asset_index_json();
    FakeHttp::new()
        .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
        .route(VERSION_URL, HttpResponse::ok(version_json()))
        .route(ASSET_INDEX_URL, HttpResponse::ok(index))
        .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR))
        .route(WIN_LIB_URL, HttpResponse::ok(WIN_LIB))
        .route(MAC_LIB_URL, HttpResponse::ok(MAC_LIB))
        .route(asset_url(ASSET_A), HttpResponse::ok(ASSET_A))
        .route(asset_url(ASSET_B), HttpResponse::ok(ASSET_B))
}

/// Records every event, so a test can assert on the shape of progress rather
/// than on a percentage.
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

async fn prepared(f: &Fixture) -> (ash_core::Plan, Arc<Recorder>) {
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();
    let sink = Recorder::new();
    let plan = f
        .ash
        .prepare_instance(&instance.id, sink.as_ref(), &Cancel::new())
        .await
        .expect("preparation succeeds");
    (plan, sink)
}

// ---- planning --------------------------------------------------------------

#[tokio::test]
async fn plans_everything_without_the_player_listing_anything() {
    let f = fixture();
    let (plan, _) = prepared(&f).await;

    // client jar + one platform's library + two assets.
    assert_eq!(plan.total_files, 4);
    assert_eq!(plan.missing_files, 4);
    assert!(plan.missing_bytes > 0);
}

#[tokio::test]
async fn rules_keep_other_platforms_libraries_out_of_the_plan() {
    let f = fixture();
    prepared(&f).await;

    let depot = f.tmp.path().join("depot");
    let win = depot.join("libraries/org/lwjgl/lwjgl-windows.jar").exists();
    let mac = depot.join("libraries/org/lwjgl/lwjgl-macos.jar").exists();

    // Exactly one, whichever platform the test runs on. Downloading both
    // would be bandwidth the player pays for and never uses.
    assert!(win ^ mac, "expected exactly one platform library, got win={win} mac={mac}");
}

#[tokio::test]
async fn everything_lands_where_a_minecraft_folder_would_put_it() {
    let f = fixture();
    prepared(&f).await;
    let depot = f.tmp.path().join("depot");

    assert!(depot.join("versions/1.21.4/1.21.4.json").is_file());
    assert!(depot.join("versions/1.21.4/1.21.4.jar").is_file());
    assert!(depot.join("assets/indexes/24.json").is_file());

    let hash = sha1(ASSET_A);
    assert!(depot.join(format!("assets/objects/{}/{}", &hash[..2], hash)).is_file());
}

#[tokio::test]
async fn every_download_comes_from_a_mojang_host() {
    let f = fixture();
    prepared(&f).await;

    // The EULA requires game downloads to come from an authorised source.
    // The depot is a cache, never a mirror.
    for url in f.http.requested() {
        assert!(
            url.contains("mojang.com") || url.contains("minecraft.net"),
            "reached a non-Mojang host: {url}"
        );
    }
}

// ---- verification ----------------------------------------------------------

#[tokio::test]
async fn a_file_that_never_matches_its_hash_fails_after_one_retry() {
    let f = fixture_with(serving().route(CLIENT_URL, HttpResponse::ok(b"corrupted".to_vec())));
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();
    let sink = Recorder::new();

    let err = f
        .ash
        .prepare_instance(&instance.id, sink.as_ref(), &Cancel::new())
        .await
        .expect_err("a corrupt file cannot be accepted");

    assert_eq!(err.kind(), "verification_failed");
    // Tried again once before giving up: transient corruption is common,
    // an identical second failure means something is really wrong.
    assert_eq!(f.http.hits(CLIENT_URL), 2);
    assert!(sink.events().iter().any(|e| matches!(e, PrepareEvent::Reverifying { .. })));
    // Nothing that failed verification is left behind wearing the real name.
    assert!(!f.tmp.path().join("depot/versions/1.21.4/1.21.4.jar").exists());
}

#[tokio::test]
async fn a_corrupt_file_that_arrives_clean_on_the_retry_succeeds() {
    let http = serving().route_sequence(
        CLIENT_URL,
        vec![HttpResponse::ok(b"corrupted".to_vec()), HttpResponse::ok(CLIENT_JAR)],
    );
    let f = fixture_with(http);
    let (_, sink) = prepared(&f).await;

    assert!(sink.events().iter().any(|e| matches!(e, PrepareEvent::Reverifying { .. })));
    assert!(f.tmp.path().join("depot/versions/1.21.4/1.21.4.jar").is_file());
}

#[tokio::test]
async fn a_tampered_asset_index_is_rejected_before_anything_is_planned_from_it() {
    let f = fixture_with(serving().route(ASSET_INDEX_URL, HttpResponse::ok(r#"{"objects":{}}"#)));
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();

    let err = f
        .ash
        .prepare_instance(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("the index decides what everything else is");

    assert_eq!(err.kind(), "verification_failed");
}

#[tokio::test]
async fn version_metadata_that_does_not_match_what_was_asked_for_is_refused() {
    let wrong = version_json().replace(r#""id":"1.21.4""#, r#""id":"1.20.1""#);
    let f = fixture_with(serving().route(VERSION_URL, HttpResponse::ok(wrong)));
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();

    let err = f
        .ash
        .prepare_instance(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("the manifest and the metadata disagree");

    // The published hash covers the body, so this is really "someone changed
    // the manifest", but refusing is right either way.
    assert!(matches!(err.kind(), "verification_failed" | "unknown_version"));
}

// ---- resume and dedup ------------------------------------------------------

#[tokio::test]
async fn an_interrupted_download_resumes_instead_of_restarting() {
    let f = fixture();
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();

    // Leave behind what a dropped connection would have: a partial file.
    let part = f.tmp.path().join("depot/versions/1.21.4/1.21.4.jar.part");
    fs::create_dir_all(part.parent().unwrap()).unwrap();
    let already = 10usize;
    fs::write(&part, &CLIENT_JAR[..already]).unwrap();

    let http = serving().route(
        CLIENT_URL,
        // 206 with only the tail, as a server honouring Range would send.
        HttpResponse::json(206, CLIENT_JAR[already..].to_vec()),
    );
    let f = Fixture { ash: f.ash, http: Arc::clone(&http), tmp: f.tmp };
    let sink = Recorder::new();

    f.ash.prepare_instance(&instance.id, sink.as_ref(), &Cancel::new()).await.unwrap();

    assert!(sink.events().iter().any(|e| matches!(
        e,
        PrepareEvent::Resuming { from_bytes, .. } if *from_bytes == already as u64
    )));
    // The reassembled file still has to satisfy the published hash.
    let final_jar = f.tmp.path().join("depot/versions/1.21.4/1.21.4.jar");
    assert_eq!(fs::read(&final_jar).unwrap(), CLIENT_JAR);
    assert!(!part.exists(), "the partial file should be gone once promoted");
}

#[tokio::test]
async fn two_instances_on_one_version_download_the_shared_content_once() {
    let f = fixture();
    let first = f.ash.create_instance("one", "1.21.4").unwrap();
    let second = f.ash.create_instance("two", "1.21.4").unwrap();

    f.ash.prepare_instance(&first.id, &ash_core::NullSink, &Cancel::new()).await.unwrap();
    let before = f.http.hits(CLIENT_URL);

    let sink = Recorder::new();
    let plan =
        f.ash.prepare_instance(&second.id, sink.as_ref(), &Cancel::new()).await.unwrap();

    assert_eq!(f.http.hits(CLIENT_URL), before, "the client jar was fetched twice");
    assert_eq!(plan.missing_files, 0, "everything was already in the depot");
    assert_eq!(plan.total_files, 4);
    assert!(sink.events().iter().any(|e| matches!(
        e,
        PrepareEvent::Planned { already_present, .. } if *already_present == 4
    )));
}

// ---- progress and cancellation ---------------------------------------------

#[tokio::test]
async fn progress_is_typed_events_not_a_percentage() {
    let f = fixture();
    let (_, sink) = prepared(&f).await;
    let events = sink.events();

    assert!(matches!(events.first(), Some(PrepareEvent::Resolving { .. })));
    assert!(matches!(events.last(), Some(PrepareEvent::Done { .. })));
    assert!(events.iter().any(|e| matches!(e, PrepareEvent::Planned { .. })));

    let downloaded: Vec<&PrepareEvent> =
        events.iter().filter(|e| matches!(e, PrepareEvent::Downloaded { .. })).collect();
    assert_eq!(downloaded.len(), 4, "one event per file");

    // The running totals have to reach the plan, or a progress bar built from
    // them would never finish.
    let final_files = events.iter().rev().find_map(|e| match e {
        PrepareEvent::Downloaded { done_files, .. } => Some(*done_files),
        _ => None,
    });
    assert_eq!(final_files, Some(4));
}

#[tokio::test]
async fn cancelling_before_any_download_stops_immediately() {
    let f = fixture();
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();
    let cancel = Cancel::new();
    cancel.cancel();
    let sink = Recorder::new();

    let err = f
        .ash
        .prepare_instance(&instance.id, sink.as_ref(), &cancel)
        .await
        .expect_err("cancelled");

    assert_eq!(err.kind(), "cancelled");
    assert!(sink.events().iter().any(|e| matches!(e, PrepareEvent::Cancelled)));
    assert_eq!(f.http.hits(CLIENT_URL), 0, "nothing was downloaded after cancelling");
}

#[tokio::test]
async fn planning_does_not_download_anything() {
    let f = fixture();
    let instance = f.ash.create_instance("smp", "1.21.4").unwrap();

    let plan = f.ash.plan_instance(&instance.id).await.unwrap();

    assert_eq!(plan.missing_files, 4);
    assert_eq!(f.http.hits(CLIENT_URL), 0);
    assert_eq!(f.http.hits(&asset_url(ASSET_A)), 0);
}

#[tokio::test]
async fn preparing_an_instance_on_a_version_mojang_no_longer_lists_says_so() {
    let f = fixture();
    let instance = f.ash.create_instance("ancient", "1.2.5").unwrap();

    let err = f
        .ash
        .prepare_instance(&instance.id, &ash_core::NullSink, &Cancel::new())
        .await
        .expect_err("not in the catalogue");

    assert_eq!(err.kind(), "unknown_version");
}
