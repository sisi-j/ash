//! Drives ash-core through its public API only.
//!
//! Nothing here touches the network or the real filesystem outside a temp
//! directory. If a test in this file ever needs to, the seam has leaked.

use std::sync::Arc;

use ash_core::http::{FakeHttp, HttpResponse};
use ash_core::{Ash, AshError, Config, VERSION_MANIFEST_URL};

const MANIFEST: &str = include_str!("fixtures/version_manifest_v2.json");

fn ash_with(http: Arc<FakeHttp>) -> (Ash, tempfile::TempDir) {
    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(Config::rooted_at(tmp.path()), http);
    (ash, tmp)
}

#[tokio::test]
async fn probes_the_manifest_through_the_http_port() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok(MANIFEST));
    let (ash, _tmp) = ash_with(Arc::clone(&http));

    let probe = ash.probe_manifest().await.expect("probe succeeds");

    assert_eq!(probe.latest_release, "1.21.4");
    assert_eq!(probe.latest_snapshot, "25w03a");
    assert_eq!(probe.total_versions, 4);

    // The seam is only worth having if we can assert what ash reached for.
    assert_eq!(http.requested(), vec![VERSION_MANIFEST_URL.to_string()]);
}

#[tokio::test]
async fn a_failing_status_becomes_a_typed_error() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::status(503));
    let (ash, _tmp) = ash_with(http);

    let err = ash.probe_manifest().await.expect_err("503 is an error");

    assert!(matches!(err, AshError::UnexpectedStatus { status: 503, .. }));
    assert_eq!(err.kind(), "unexpected_status");
}

#[tokio::test]
async fn malformed_json_becomes_a_typed_error() {
    let http = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok("{ not json"));
    let (ash, _tmp) = ash_with(http);

    let err = ash.probe_manifest().await.expect_err("garbage is an error");

    assert_eq!(err.kind(), "malformed");
}

#[tokio::test]
async fn user_messages_never_leak_urls_or_library_text() {
    let cases = [
        HttpResponse::status(503),
        HttpResponse::ok("{ not json"),
    ];

    for response in cases {
        let http = FakeHttp::new().route(VERSION_MANIFEST_URL, response);
        let (ash, _tmp) = ash_with(http);

        let err = ash.probe_manifest().await.expect_err("error expected");
        let message = err.user_message();

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

    // Instances and depot are separate roots - ADR-0008 depends on deleting
    // one never touching the other.
    assert_ne!(config.depot_root, config.instances_root);
}
