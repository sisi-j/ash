//! The News page's data (spec 0004, #126), against a fake backend serving
//! ash-backend's own `contract/news.json` (copied to `tests/fixtures/backend`).

use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, NewsKind};

const BACKEND: &str = "https://api.ash.test";
const NEWS_URL: &str = "https://api.ash.test/v1/news";

fn contract() -> serde_json::Value {
    let path = format!("{}/tests/fixtures/backend/news.json", env!("CARGO_MANIFEST_DIR"));
    serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap()
}

/// The contract's post, plus `extra` newer posts with the given times.
fn feed(newer: &[u64]) -> HttpResponse {
    let mut body = contract();
    let example = body["posts"][0].clone();
    let posts = body["posts"].as_array_mut().unwrap();
    for (i, published_at) in newer.iter().enumerate() {
        let mut post = example.clone();
        post["id"] = format!("newer-{i}").into();
        post["kind"] = "news".into();
        post["cover_url"] = serde_json::Value::Null;
        post["published_at"] = (*published_at).into();
        posts.insert(0, post);
    }
    HttpResponse::ok(body.to_string())
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

#[tokio::test]
async fn the_news_is_what_ash_published_with_nothing_lost_on_the_way() {
    let tmp = tempfile::tempdir().unwrap();
    let ash = ash(FakeHttp::new().route(NEWS_URL, feed(&[])), tmp.path());

    let news = ash.news().await;

    assert!(!news.offline);
    assert_eq!(news.posts.len(), 1);
    let post = &news.posts[0];
    let example = &contract()["posts"][0];
    assert_eq!(post.kind, NewsKind::PatchNotes);
    assert_eq!(post.title, example["title"].as_str().unwrap());
    assert_eq!(post.body, example["body"].as_str().unwrap());
    assert_eq!(post.cover_url.as_deref(), example["cover_url"].as_str());
    assert_eq!(post.published_at, example["published_at"].as_u64().unwrap());
}

#[tokio::test]
async fn offline_shows_the_last_news_ash_saw_and_says_so() {
    let tmp = tempfile::tempdir().unwrap();
    ash(FakeHttp::new().route(NEWS_URL, feed(&[])), tmp.path()).news().await;

    let offline = ash(FakeHttp::new().host_unreachable(BACKEND), tmp.path()).news().await;

    assert!(offline.offline);
    assert_eq!(offline.posts.len(), 1, "the copy kept from last time");
}

#[tokio::test]
async fn offline_before_ever_reaching_ash_is_an_empty_page_not_an_error() {
    let tmp = tempfile::tempdir().unwrap();

    let news =
        ash(FakeHttp::new().route(NEWS_URL, HttpResponse::status(503)), tmp.path()).news().await;

    assert!(news.offline && news.posts.is_empty() && !news.unread);
}

#[tokio::test]
async fn a_post_is_unread_until_the_page_is_opened_and_a_newer_one_is_unread_again() {
    let tmp = tempfile::tempdir().unwrap();
    let first = ash(FakeHttp::new().route(NEWS_URL, feed(&[])), tmp.path());
    assert!(first.news().await.unread, "never seen anything");

    first.mark_news_seen().unwrap();
    assert!(!first.news().await.unread);

    let newer = contract()["posts"][0]["published_at"].as_u64().unwrap() + 1;
    let later = ash(FakeHttp::new().route(NEWS_URL, feed(&[newer])), tmp.path());
    assert!(later.news().await.unread, "a newer post arrived");
    later.mark_news_seen().unwrap();
    assert!(!later.news().await.unread);
}

#[tokio::test]
async fn an_unpublished_post_never_makes_an_older_one_unread_again() {
    let tmp = tempfile::tempdir().unwrap();
    let newer = contract()["posts"][0]["published_at"].as_u64().unwrap() + 1;
    let with_newer = ash(FakeHttp::new().route(NEWS_URL, feed(&[newer])), tmp.path());
    with_newer.news().await;
    with_newer.mark_news_seen().unwrap();

    // The newer post is withdrawn; only the older one is left.
    let withdrawn = ash(FakeHttp::new().route(NEWS_URL, feed(&[])), tmp.path());
    withdrawn.news().await;
    withdrawn.mark_news_seen().unwrap();

    assert!(!withdrawn.news().await.unread);
}
