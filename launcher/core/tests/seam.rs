//! Invariants of the seam itself, independent of any one feature.
//!
//! Nothing here touches the network or the real filesystem outside a temp
//! directory. If a test in this file ever needs to, the seam has leaked.

use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpResponse};
use ash_core::{Ash, AshError, Config, VERSION_MANIFEST_URL};

const MANIFEST: &str = include_str!("fixtures/version_manifest_v2.json");

fn ash_with(http: Arc<FakeHttp>) -> (Ash, tempfile::TempDir) {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(Config::rooted_at(tmp.path()), http, InMemoryCredentialStore::new(), "test-client");
    (ash, tmp)
}

#[tokio::test]
async fn reaches_only_for_what_it_needs() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok(MANIFEST));
    let (ash, _tmp) = ash_with(Arc::clone(&http));

    ash.catalogue().await.expect("catalogue loads");

    // The port is only worth having if we can assert what ash asked for.
    assert_eq!(http.requested(), vec![VERSION_MANIFEST_URL.to_string()]);
}

#[tokio::test]
async fn a_failing_status_becomes_a_typed_error() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::status(503));
    let (ash, _tmp) = ash_with(http);

    let err = ash.catalogue().await.expect_err("503 with no cache is an error");

    assert!(matches!(err, AshError::UnexpectedStatus { status: 503, .. }));
    assert_eq!(err.kind(), "unexpected_status");
}

#[tokio::test]
async fn malformed_json_becomes_a_typed_error() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok("{ not json"));
    let (ash, _tmp) = ash_with(http);

    let err = ash.catalogue().await.expect_err("garbage is an error");

    assert_eq!(err.kind(), "malformed");
}

#[tokio::test]
async fn user_messages_never_leak_urls_or_library_text() {
    for response in [HttpResponse::status(503), HttpResponse::ok("{ not json")] {
        let http = FakeHttp::new().route(VERSION_MANIFEST_URL, response);
        let (ash, _tmp) = ash_with(http);

        let message = ash.catalogue().await.expect_err("error expected").user_message();

        assert!(!message.contains("http"), "leaked a URL: {message}");
        assert!(!message.contains("mojang.com"), "leaked a host: {message}");
        assert!(!message.is_empty());
    }
}

#[test]
fn config_roots_are_plain_values_under_a_caller_chosen_base() {
    let tmp = tempfile::tempdir().expect("temp dir");
    let config = Config::rooted_at(tmp.path());

    assert_eq!(config.depot_root, tmp.path().join("depot"));
    assert_eq!(config.instances_root, tmp.path().join("instances"));

    // ADR-0008 depends on deleting an instance never touching the depot, which
    // starts with them not being the same directory.
    assert_ne!(config.depot_root, config.instances_root);
}
