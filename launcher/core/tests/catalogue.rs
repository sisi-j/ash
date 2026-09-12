//! Catalogue behaviour: what ash shows, what it caches, and what it does when
//! Mojang is unreachable.

use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::process::FakeProcessPort;
use ash_core::http::{FakeHttp, HttpResponse};
use ash_core::{Ash, Config, CatalogueSource, VersionKind, VERSION_MANIFEST_URL};

const MANIFEST: &str = include_str!("fixtures/version_manifest_v2.json");

fn serving_manifest() -> Arc<FakeHttp> {
    FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok(MANIFEST))
}

/// A port with no routes at all: any request panics. Used to prove ash did
/// not go to the network.
fn offline() -> Arc<FakeHttp> {
    FakeHttp::new()
}

fn ash_with(http: Arc<FakeHttp>, tmp: &tempfile::TempDir) -> Ash {
    Ash::new(
        Config::rooted_at(tmp.path()),
        http,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        "test-client",
    )
}

#[tokio::test]
async fn reads_the_headline_versions() {
    let tmp = tempfile::tempdir().unwrap();
    let catalogue = ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    assert_eq!(catalogue.latest_release, "1.21.4");
    assert_eq!(catalogue.latest_snapshot, "25w03a");
    assert_eq!(catalogue.versions.len(), 10);
    assert_eq!(catalogue.source, CatalogueSource::Network);
}

#[tokio::test]
async fn marks_exactly_the_first_class_targets() {
    let tmp = tempfile::tempdir().unwrap();
    let catalogue = ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let first_class: Vec<&str> =
        catalogue.first_class().map(|v| v.id.as_str()).collect();

    // ADR-0005: 1.8.9 and the newest 1.21.x. Not 1.21.3, not 1.21, not 1.8.
    assert_eq!(first_class, vec!["1.21.4", "1.8.9"]);
}

#[tokio::test]
async fn releases_exclude_snapshots_and_older_eras() {
    let tmp = tempfile::tempdir().unwrap();
    let catalogue = ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let releases: Vec<&str> = catalogue.releases().map(|v| v.id.as_str()).collect();

    assert_eq!(releases, vec!["1.21.4", "1.21.3", "1.21", "1.20.4", "1.8.9", "1.8"]);
}

#[tokio::test]
async fn an_unrecognised_version_type_is_kept_not_dropped() {
    let tmp = tempfile::tempdir().unwrap();
    let catalogue = ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let experiment = catalogue
        .versions
        .iter()
        .find(|v| v.id == "exp-1")
        .expect("an unknown type must survive parsing");

    assert_eq!(experiment.kind, VersionKind::Other);
}

#[tokio::test]
async fn a_second_load_serves_the_cache_without_touching_the_network() {
    let tmp = tempfile::tempdir().unwrap();
    ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    // `offline()` panics on any request, so reaching the network fails loudly.
    let cached = ash_with(offline(), &tmp).catalogue().await.unwrap();

    assert_eq!(cached.source, CatalogueSource::Cache);
    assert_eq!(cached.latest_release, "1.21.4");
    assert_eq!(cached.versions.len(), 10);
}

#[tokio::test]
async fn first_class_marking_survives_the_cache_round_trip() {
    let tmp = tempfile::tempdir().unwrap();
    ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let cached = ash_with(offline(), &tmp).catalogue().await.unwrap();
    let first_class: Vec<&str> = cached.first_class().map(|v| v.id.as_str()).collect();

    assert_eq!(first_class, vec!["1.21.4", "1.8.9"]);
}

#[tokio::test]
async fn refresh_falls_back_to_cache_when_mojang_is_unreachable() {
    let tmp = tempfile::tempdir().unwrap();
    ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let down = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::status(503));
    let catalogue = ash_with(down, &tmp).refresh_catalogue().await.unwrap();

    // The player still sees their versions, and is told the data is stale
    // rather than being shown a refresh that quietly did nothing.
    assert_eq!(catalogue.source, CatalogueSource::Cache);
    assert_eq!(catalogue.latest_release, "1.21.4");
}

#[tokio::test]
async fn refresh_with_no_cache_propagates_the_failure() {
    let tmp = tempfile::tempdir().unwrap();
    let down = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::status(503));

    let err = ash_with(down, &tmp).refresh_catalogue().await.expect_err("nothing to fall back to");

    assert_eq!(err.kind(), "unexpected_status");
}

#[tokio::test]
async fn the_fetch_time_is_recorded_and_not_reset_by_reading_the_cache() {
    let tmp = tempfile::tempdir().unwrap();
    let before = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;

    let fresh = ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();
    let after = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;

    assert!((before..=after).contains(&fresh.fetched_at_ms));

    // Reading the cache must report when the data was *fetched*, not when it
    // was read - otherwise the UI would always claim it was just refreshed.
    let cached = ash_with(offline(), &tmp).catalogue().await.unwrap();
    assert_eq!(cached.fetched_at_ms, fresh.fetched_at_ms);
}

#[tokio::test]
async fn a_successful_refresh_overwrites_the_cache() {
    let tmp = tempfile::tempdir().unwrap();
    ash_with(serving_manifest(), &tmp).catalogue().await.unwrap();

    let newer = MANIFEST.replace("1.21.4", "1.21.5");
    let updated = FakeHttp::new().route(VERSION_MANIFEST_URL, HttpResponse::ok(newer));
    ash_with(updated, &tmp).refresh_catalogue().await.unwrap();

    let cached = ash_with(offline(), &tmp).catalogue().await.unwrap();
    assert_eq!(cached.latest_release, "1.21.5");
}
