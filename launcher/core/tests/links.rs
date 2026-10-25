//! The launcher's links (spec 0005): the Discord invite behind "Send
//! feedback", from ash's backend, kept for when it can't be reached.

use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, Links};

const BACKEND: &str = "https://api.ash.test";
const LINKS_URL: &str = "https://api.ash.test/v1/links";

fn contract() -> String {
    std::fs::read_to_string(format!(
        "{}/tests/fixtures/backend/links.json",
        env!("CARGO_MANIFEST_DIR")
    ))
    .unwrap()
}

fn ash(http: Arc<FakeHttp>, root: &std::path::Path) -> Ash {
    Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(root) },
        http as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    )
}

fn invite() -> Option<String> {
    let example: serde_json::Value = serde_json::from_str(&contract()).unwrap();
    example["feedback_url"].as_str().map(str::to_owned)
}

#[tokio::test]
async fn the_feedback_link_is_the_backend_s_and_is_kept_for_when_it_can_t_be_reached() {
    let tmp = tempfile::tempdir().unwrap();
    let online = ash(FakeHttp::new().route(LINKS_URL, HttpResponse::ok(contract())), tmp.path());
    assert_eq!(online.cached_links(), Links::default(), "nothing seen yet");

    assert_eq!(online.links().await.feedback_url, invite());

    let offline = ash(FakeHttp::new().host_unreachable(BACKEND), tmp.path());
    assert_eq!(offline.links().await.feedback_url, invite(), "the last one seen");
    assert_eq!(offline.cached_links().feedback_url, invite());
}

#[tokio::test]
async fn before_any_is_seen_there_is_no_link_rather_than_a_dead_one() {
    let tmp = tempfile::tempdir().unwrap();
    let offline = ash(FakeHttp::new().host_unreachable(BACKEND), tmp.path());
    assert_eq!(offline.links().await, Links::default());
}

#[tokio::test]
async fn anything_but_a_discord_invite_from_the_backend_is_never_opened() {
    let tmp = tempfile::tempdir().unwrap();
    let odd = r#"{"feedback_url":"https://evil.example/login"}"#;
    let f = ash(FakeHttp::new().route(LINKS_URL, HttpResponse::ok(odd)), tmp.path());
    assert_eq!(f.links().await.feedback_url, None);
    assert_eq!(f.cached_links().feedback_url, None);
}

#[tokio::test]
async fn the_first_run_note_stays_dismissed_on_this_machine() {
    let tmp = tempfile::tempdir().unwrap();
    let f = ash(FakeHttp::new(), tmp.path());
    assert!(!f.first_run_note_dismissed());

    f.dismiss_first_run_note().unwrap();

    assert!(ash(FakeHttp::new(), tmp.path()).first_run_note_dismissed(), "after a restart too");
}

#[tokio::test]
async fn the_tour_shows_once_on_this_machine() {
    let tmp = tempfile::tempdir().unwrap();
    let f = ash(FakeHttp::new(), tmp.path());
    assert!(!f.tour_seen());

    f.mark_tour_seen().unwrap();

    assert!(ash(FakeHttp::new(), tmp.path()).tour_seen(), "after a restart too");
    assert!(!f.first_run_note_dismissed(), "the alpha note is its own");
}
