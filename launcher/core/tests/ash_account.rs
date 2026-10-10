//! Signing a player in to their ash account (spec 0004, ADR-0020), against a
//! fake of ash's backend that answers with the backend's own contract
//! examples - `tests/fixtures/backend/`, copied from sisi-j/ash-backend's
//! `contract/` and checked against it in CI.

use std::sync::Arc;

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, AshAccountStatus, Config};

mod common;

const BACKEND: &str = "https://api.ash.test";
const CHALLENGE_URL: &str = "https://api.ash.test/v1/session/challenge";
const SESSION_URL: &str = "https://api.ash.test/v1/session";
const JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";

fn contract(name: &str) -> serde_json::Value {
    let path = format!("{}/tests/fixtures/backend/{name}.json", env!("CARGO_MANIFEST_DIR"));
    serde_json::from_slice(&std::fs::read(path).expect("contract example")).expect("json")
}

/// The backend's session answer, as its contract example, with `created`
/// and an expiry chosen by the test.
fn session_answer(created: bool, expires_in_ms: i64) -> HttpResponse {
    let mut body = contract("session");
    body["account"]["created"] = created.into();
    body["account"]["uuid"] = common::PLAYER_UUID.into();
    body["account"]["username"] = common::PLAYER_NAME.into();
    body["session"]["expires_at"] = (now_ms() + expires_in_ms).into();
    HttpResponse::ok(body.to_string())
}

const DAY_MS: i64 = 24 * 60 * 60 * 1000;

fn now_ms() -> i64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as i64
}

/// Everything answering: the Microsoft chain, Mojang's join, and the backend.
fn serving() -> Arc<FakeHttp> {
    common::with_auth_routes(FakeHttp::new())
        .route(JOIN_URL, HttpResponse::status(204))
        .route(CHALLENGE_URL, HttpResponse::ok(contract("session-challenge").to_string()))
        .route(SESSION_URL, session_answer(true, 30 * DAY_MS))
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    store: Arc<InMemoryCredentialStore>,
    _tmp: tempfile::TempDir,
}

async fn signed_in(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let store = InMemoryCredentialStore::new();
    let ash = Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(tmp.path()) },
        Arc::clone(&http) as Arc<dyn HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    ash.begin_sign_in().await.expect("device code");
    ash.poll_sign_in().await.expect("signed in to Microsoft");
    Fixture { ash, http, store, _tmp: tmp }
}

fn notice(f: &Fixture) -> bool {
    f.ash.accounts().active_account().expect("an account").ash_account_notice
}

// ---- signing in ---------------------------------------------------------------

#[tokio::test]
async fn signing_in_to_ash_joins_at_mojang_and_keeps_the_session_in_the_credential_store() {
    let f = signed_in(serving()).await;

    let status = f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    assert_eq!(status, AshAccountStatus::SignedIn);
    assert_eq!(f.http.hits(CHALLENGE_URL), 1);
    assert_eq!(f.http.hits(JOIN_URL), 1, "the join is what proves who the player is");
    let session_body: serde_json::Value =
        serde_json::from_str(&f.http.last_body(SESSION_URL).expect("redeemed")).unwrap();
    assert_eq!(session_body["challenge"], contract("session-challenge")["challenge"]);
    assert_eq!(session_body["username"], common::PLAYER_NAME);

    let stored = f.store.get(&format!("ash-session:{}", common::PLAYER_UUID)).unwrap();
    assert!(stored
        .expect("kept")
        .contains(contract("session")["session"]["token"].as_str().unwrap()));
    assert_eq!(f.ash.ash_account_status(common::PLAYER_UUID), AshAccountStatus::SignedIn);
}

#[tokio::test]
async fn no_request_to_ash_carries_a_microsoft_or_minecraft_token() {
    let f = signed_in(serving()).await;

    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    let sent = f.http.everything_sent_to(BACKEND);
    // Guards against a vacuous pass: the challenge and the redemption.
    assert_eq!(sent.len(), 2, "{sent:#?}");
    for request in &sent {
        for secret in [common::MC_TOKEN, "MS-ACCESS", "MS-REFRESH", "XBL", "XSTS"] {
            assert!(!request.contains(secret), "{secret} reached ash's backend:\n{request}");
        }
    }
    // And the Minecraft token did go to Mojang - so the check above was able
    // to see it, had it been sent the wrong way.
    assert!(f.http.last_body(JOIN_URL).unwrap().contains(common::MC_TOKEN));
}

// ---- the notice -----------------------------------------------------------------

#[tokio::test]
async fn a_new_ash_account_is_noticed_once_until_dismissed() {
    let f = signed_in(serving()).await;
    assert!(!notice(&f), "no notice before ash made an account");

    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    assert!(notice(&f), "ash made an account, so the player is told");

    f.ash.dismiss_ash_account_notice(common::PLAYER_UUID).expect("dismissed");
    assert!(!notice(&f));
}

#[tokio::test]
async fn signing_in_to_an_existing_ash_account_raises_no_notice() {
    let f = signed_in(serving().route(SESSION_URL, session_answer(false, 30 * DAY_MS))).await;

    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    assert!(!notice(&f));
}

// ---- failure never costs a launch -----------------------------------------------

#[tokio::test]
async fn ash_being_unreachable_is_a_status_not_an_error() {
    let f = signed_in(serving().host_unreachable(BACKEND)).await;

    let status = f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    assert_eq!(status, AshAccountStatus::Unreachable);
    assert!(f.store.get(&format!("ash-session:{}", common::PLAYER_UUID)).unwrap().is_none());
    assert!(!notice(&f));
}

#[tokio::test]
async fn any_refusal_from_ash_is_unreachable_and_tried_again_later() {
    for answer in [
        HttpResponse::json(401, contract("error").to_string()),
        HttpResponse::json(503, r#"{"error":{"kind":"mojang_unavailable","message":"x"}}"#),
        HttpResponse::ok("not json"),
    ] {
        let f = signed_in(serving().route(SESSION_URL, answer)).await;
        assert_eq!(f.ash.sign_in_to_ash(common::PLAYER_UUID).await, AshAccountStatus::Unreachable);
    }
}

#[tokio::test]
async fn a_microsoft_account_without_multiplayer_is_told_why_and_still_plays() {
    let f = signed_in(serving().route(
        JOIN_URL,
        HttpResponse::json(403, r#"{"error":"InsufficientPrivilegesException"}"#),
    ))
    .await;

    let status = f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    let AshAccountStatus::Refused { kind, message } = status else {
        panic!("expected a refusal, got {status:?}");
    };
    assert_eq!(kind, "multiplayer_disabled");
    assert!(message.contains("Playing is unaffected"), "{message}");
    assert_eq!(f.http.hits(SESSION_URL), 0, "nothing to redeem without a join");
}

#[tokio::test]
async fn refreshing_for_a_launch_never_asks_ash() {
    let f = signed_in(serving()).await;

    f.ash.ensure_session(common::PLAYER_UUID).await.expect("refreshed");

    assert!(f.http.everything_sent_to(BACKEND).is_empty(), "a launch must not wait on ash");
}

// ---- when to sign in again --------------------------------------------------------

#[tokio::test]
async fn a_session_is_renewed_when_missing_or_within_a_week_of_ending() {
    let fresh = signed_in(serving()).await;
    assert!(fresh.ash.needs_ash_sign_in(common::PLAYER_UUID), "no session yet");
    fresh.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    assert!(!fresh.ash.needs_ash_sign_in(common::PLAYER_UUID), "30 days left");

    let ending = signed_in(serving().route(SESSION_URL, session_answer(false, 6 * DAY_MS))).await;
    ending.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    assert!(ending.ash.needs_ash_sign_in(common::PLAYER_UUID), "6 days left");
}

#[tokio::test]
async fn an_ended_session_found_at_startup_is_not_signed_in() {
    let f = signed_in(serving().route(SESSION_URL, session_answer(false, -DAY_MS))).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    // A new run of the launcher, with no attempt this run: judged by the store.
    let next_run = Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(f._tmp.path()) },
        Arc::clone(&f.http) as Arc<dyn HttpPort>,
        Arc::clone(&f.store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    assert_eq!(next_run.ash_account_status(common::PLAYER_UUID), AshAccountStatus::NotSignedIn);
}

// ---- deleting the ash account (sisi-j/ash#120) ---------------------------------

const ACCOUNT_URL: &str = "https://api.ash.test/v1/account";

fn session_in_store(f: &Fixture) -> Option<String> {
    f.store.get(&format!("ash-session:{}", common::PLAYER_UUID)).unwrap()
}

#[tokio::test]
async fn deleting_asks_ash_with_the_session_then_forgets_it_and_stays_signed_in_to_play() {
    let f = signed_in(serving().route(ACCOUNT_URL, HttpResponse::status(204))).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    let accounts = f.ash.delete_ash_account(common::PLAYER_UUID).await.expect("deleted");

    let sent = f.http.everything_sent_to(ACCOUNT_URL);
    assert_eq!(sent.len(), 1);
    let token = contract("session")["session"]["token"].as_str().unwrap().to_owned();
    assert!(sent[0].starts_with("Delete "), "{}", sent[0]);
    assert!(sent[0].contains(&format!("Bearer {token}")), "{}", sent[0]);
    assert!(session_in_store(&f).is_none(), "the ash session is gone");
    assert!(
        f.store.get(&format!("refresh-token:{}", common::PLAYER_UUID)).unwrap().is_some(),
        "still signed in to play"
    );
    let player = accounts.active_account().expect("still the active account");
    assert!(player.ash_account_deleted && !player.ash_account_notice);
    assert_eq!(f.ash.ash_account_status(common::PLAYER_UUID), AshAccountStatus::Deleted);
}

#[tokio::test]
async fn a_deleted_ash_account_is_not_made_again_in_the_background() {
    let f = signed_in(serving().route(ACCOUNT_URL, HttpResponse::status(204))).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    f.ash.delete_ash_account(common::PLAYER_UUID).await.expect("deleted");
    let before = f.http.everything_sent_to(BACKEND).len();

    assert!(!f.ash.needs_ash_sign_in(common::PLAYER_UUID), "startup must not renew it");
    assert_eq!(f.ash.sign_in_to_ash(common::PLAYER_UUID).await, AshAccountStatus::Deleted);
    assert_eq!(f.http.everything_sent_to(BACKEND).len(), before, "nothing was sent to ash");
}

#[tokio::test]
async fn signing_in_to_microsoft_again_makes_a_fresh_ash_account_and_says_so() {
    let f = signed_in(serving().route(ACCOUNT_URL, HttpResponse::status(204))).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    f.ash.dismiss_ash_account_notice(common::PLAYER_UUID).unwrap();
    f.ash.delete_ash_account(common::PLAYER_UUID).await.expect("deleted");

    f.ash.begin_sign_in().await.expect("device code");
    f.ash.poll_sign_in().await.expect("signed in again");
    let status = f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    assert_eq!(status, AshAccountStatus::SignedIn);
    assert!(notice(&f), "the backend made a new account, so the player is told again");
}

#[tokio::test]
async fn a_deletion_ash_could_not_confirm_changes_nothing_here() {
    let f = signed_in(serving().route(ACCOUNT_URL, HttpResponse::status(503))).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    let err = f.ash.delete_ash_account(common::PLAYER_UUID).await.expect_err("not confirmed");

    assert!(err.is_retryable());
    assert!(session_in_store(&f).is_some(), "the session stays for another try");
    assert!(!f.ash.accounts().active_account().unwrap().ash_account_deleted);
    assert_eq!(f.ash.ash_account_status(common::PLAYER_UUID), AshAccountStatus::SignedIn);
}

#[tokio::test]
async fn deleting_with_an_ended_session_signs_in_first() {
    let f = signed_in(
        serving()
            .route(ACCOUNT_URL, HttpResponse::status(204))
            .route(SESSION_URL, session_answer(false, -DAY_MS)),
    )
    .await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;
    let redeemed_before = f.http.hits(SESSION_URL);

    // The renewal answers with a live session this time.
    f.http.route(SESSION_URL, session_answer(false, 30 * DAY_MS));
    f.ash.delete_ash_account(common::PLAYER_UUID).await.expect("deleted");

    assert_eq!(f.http.hits(SESSION_URL), redeemed_before + 1, "renewed before deleting");
    assert_eq!(f.http.hits(ACCOUNT_URL), 1);
}

// ---- signing out --------------------------------------------------------------------

#[tokio::test]
async fn signing_out_forgets_the_ash_session_too() {
    let f = signed_in(serving()).await;
    f.ash.sign_in_to_ash(common::PLAYER_UUID).await;

    f.ash.remove_account(common::PLAYER_UUID).expect("signed out");

    assert!(f.store.get(&format!("ash-session:{}", common::PLAYER_UUID)).unwrap().is_none());
}
