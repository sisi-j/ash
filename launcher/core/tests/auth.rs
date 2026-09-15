//! The Microsoft sign-in chain, driven entirely from fixtures.
//!
//! Hops 1 to 4 work against the real services today. Hops 5 to 7 return
//! `403 Invalid app registration` until ash's client id is allow-listed, so
//! these fixtures are hand-written from the shapes recorded in
//! `docs/research/0001-minecraft-launcher-api-access.md`. When approval lands,
//! replace them with captured responses and these tests should still pass -
//! if they don't, the recorded shapes were wrong and that is worth knowing.

use std::sync::Arc;

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::{Ash, AshError, Config, SignInStatus};

const CLIENT_ID: &str = "d8cc6384-820e-4608-9a60-f05da43d3571";

const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/launcher/login";
const MC_ENTITLEMENTS_URL: &str = "https://api.minecraftservices.com/entitlements/license";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

// ---- fixtures --------------------------------------------------------------

fn device_code(expires_in: u64) -> HttpResponse {
    HttpResponse::ok(format!(
        r#"{{"device_code":"DEV-CODE","user_code":"WXYZ-ABCD",
            "verification_uri":"https://microsoft.com/link","expires_in":{expires_in},
            "interval":5}}"#
    ))
}

fn token_pending() -> HttpResponse {
    HttpResponse::json(400, r#"{"error":"authorization_pending"}"#)
}

fn token_error(code: &str) -> HttpResponse {
    HttpResponse::json(400, format!(r#"{{"error":"{code}"}}"#))
}

fn token_success() -> HttpResponse {
    HttpResponse::ok(r#"{"access_token":"MS-ACCESS","refresh_token":"MS-REFRESH"}"#)
}

fn xbox_ok(token: &str) -> HttpResponse {
    HttpResponse::ok(format!(
        r#"{{"Token":"{token}","DisplayClaims":{{"xui":[{{"uhs":"USERHASH"}}]}}}}"#
    ))
}

fn xsts_denied(xerr: u64) -> HttpResponse {
    HttpResponse::json(401, format!(r#"{{"Identity":"0","XErr":{xerr},"Message":""}}"#))
}

fn mc_login_ok() -> HttpResponse {
    HttpResponse::ok(r#"{"username":"uuid","access_token":"MC-TOKEN","expires_in":86400}"#)
}

fn entitlements(items: &str) -> HttpResponse {
    HttpResponse::ok(format!(r#"{{"items":[{items}],"signature":"sig","keyId":"1"}}"#))
}

fn owns_the_game() -> HttpResponse {
    entitlements(
        r#"{"name":"product_minecraft","signature":"s"},{"name":"game_minecraft","signature":"s"}"#,
    )
}

fn profile_ok() -> HttpResponse {
    HttpResponse::ok(
        r#"{"id":"986DEC87-B7EC-47FF-89FF-033FDB95C4B5","name":"Notch",
            "skins":[{"id":"s","state":"ACTIVE","url":"https://textures/abc","variant":"CLASSIC"}]}"#,
    )
}

/// Every hop routed for a clean sign-in. Individual tests override one hop to
/// exercise a failure without restating the other six.
fn happy_chain() -> Arc<FakeHttp> {
    FakeHttp::new()
        .route(DEVICE_CODE_URL, device_code(900))
        .route(TOKEN_URL, token_success())
        .route(XBL_URL, xbox_ok("XBL-TOKEN"))
        .route(XSTS_URL, xbox_ok("XSTS-TOKEN"))
        .route(MC_LOGIN_URL, mc_login_ok())
        .route(MC_ENTITLEMENTS_URL, owns_the_game())
        .route(MC_PROFILE_URL, profile_ok())
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    store: Arc<InMemoryCredentialStore>,
    _tmp: tempfile::TempDir,
}

fn fixture(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let store = InMemoryCredentialStore::new();
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        Arc::clone(&http) as Arc<dyn ash_core::http::HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        CLIENT_ID,
    );
    Fixture { ash, http, store, _tmp: tmp }
}

async fn sign_in(f: &Fixture) -> Result<SignInStatus, AshError> {
    f.ash.begin_sign_in().await?;
    f.ash.poll_sign_in().await
}

// ---- the happy path --------------------------------------------------------

#[tokio::test]
async fn a_complete_sign_in_produces_an_account_keyed_by_profile_uuid() {
    let f = fixture(happy_chain());

    let status = sign_in(&f).await.expect("sign-in completes");

    let SignInStatus::Complete { account } = status else {
        panic!("expected completion, got {status:?}");
    };
    // ADR-0009: undashed, lowercase, and never the Microsoft account id.
    assert_eq!(account.profile_id, "986dec87b7ec47ff89ff033fdb95c4b5");
    assert_eq!(account.username, "Notch");
    assert_eq!(account.skin_url.as_deref(), Some("https://textures/abc"));
}

#[tokio::test]
async fn the_player_is_given_a_code_and_somewhere_to_type_it() {
    let f = fixture(happy_chain());

    let pending = f.ash.begin_sign_in().await.expect("begins");

    assert_eq!(pending.user_code, "WXYZ-ABCD");
    assert_eq!(pending.verification_uri, "https://microsoft.com/link");
    assert_eq!(pending.interval_secs, 5);
    assert!(pending.expires_at_ms > 0);
}

#[tokio::test]
async fn the_whole_chain_is_walked_in_order() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    let urls = f.http.requested();
    let hops: Vec<&str> = urls.iter().map(|u| u.split('?').next().unwrap()).collect();
    assert_eq!(
        hops,
        vec![
            DEVICE_CODE_URL,
            TOKEN_URL,
            XBL_URL,
            XSTS_URL,
            MC_LOGIN_URL,
            MC_ENTITLEMENTS_URL,
            MC_PROFILE_URL
        ]
    );
}

#[tokio::test]
async fn the_xbox_ticket_carries_the_d_prefix() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    // Every reference implementation carries it, and sign-in fails without.
    let body = f.http.last_body(XBL_URL).expect("a body was sent");
    assert!(body.contains(r#""RpsTicket":"d=MS-ACCESS""#), "got {body}");
}

#[tokio::test]
async fn the_minecraft_hops_carry_a_bearer_token() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    for url in [MC_ENTITLEMENTS_URL, MC_PROFILE_URL] {
        let headers = f.http.last_headers(url);
        assert!(
            headers.iter().any(|(k, v)| k == "Authorization" && v == "Bearer MC-TOKEN"),
            "{url} went out without a bearer token: {headers:?}"
        );
    }
}

#[tokio::test]
async fn the_token_request_uses_the_device_code_grant_and_our_client_id() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    let body = f.http.last_body(TOKEN_URL).expect("a body was sent");
    assert!(body.contains("device_code=DEV-CODE"), "got {body}");
    assert!(body.contains(&format!("client_id={CLIENT_ID}")), "got {body}");
    assert!(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
}

// ---- polling ---------------------------------------------------------------

#[tokio::test]
async fn polling_waits_while_the_player_has_not_approved_yet() {
    let http = happy_chain()
        .route_sequence(TOKEN_URL, vec![token_pending(), token_pending(), token_success()]);
    let f = fixture(http);

    f.ash.begin_sign_in().await.unwrap();
    assert!(matches!(f.ash.poll_sign_in().await.unwrap(), SignInStatus::Waiting { .. }));
    assert!(matches!(f.ash.poll_sign_in().await.unwrap(), SignInStatus::Waiting { .. }));
    assert!(matches!(f.ash.poll_sign_in().await.unwrap(), SignInStatus::Complete { .. }));
}

#[tokio::test]
async fn slow_down_raises_the_interval_rather_than_failing() {
    let http = happy_chain().route_sequence(TOKEN_URL, vec![token_error("slow_down")]);
    let f = fixture(http);

    f.ash.begin_sign_in().await.unwrap();
    let status = f.ash.poll_sign_in().await.expect("slow_down is not fatal");

    // Not in Microsoft's documented error table, but RFC 8628 defines it and
    // treating it as fatal would fail a sign-in that was going fine.
    let SignInStatus::Waiting { interval_secs } = status else {
        panic!("expected to keep waiting, got {status:?}");
    };
    assert!(interval_secs > 5, "the interval should back off, got {interval_secs}");
}

#[tokio::test]
async fn a_declined_sign_in_is_distinct_from_an_expired_one() {
    for (code, expected) in
        [("authorization_declined", "sign_in_declined"), ("expired_token", "sign_in_expired")]
    {
        let http = happy_chain().route_sequence(TOKEN_URL, vec![token_error(code)]);
        let f = fixture(http);

        f.ash.begin_sign_in().await.unwrap();
        let err = f.ash.poll_sign_in().await.expect_err("terminal outcome");

        assert_eq!(err.kind(), expected);
    }
}

#[tokio::test]
async fn an_already_expired_code_is_caught_without_asking_microsoft() {
    let http = happy_chain().route(DEVICE_CODE_URL, device_code(0));
    let f = fixture(http);

    f.ash.begin_sign_in().await.unwrap();
    let err = f.ash.poll_sign_in().await.expect_err("already expired");

    assert_eq!(err.kind(), "sign_in_expired");
    // No point asking a service about a code we know is dead.
    assert_eq!(f.http.hits(TOKEN_URL), 0);
}

#[tokio::test]
async fn polling_without_starting_says_so() {
    let f = fixture(happy_chain());
    let err = f.ash.poll_sign_in().await.expect_err("nothing in progress");
    assert_eq!(err.kind(), "no_sign_in_pending");
}

#[tokio::test]
async fn a_terminated_sign_in_cannot_be_polled_again() {
    let http = happy_chain().route_sequence(TOKEN_URL, vec![token_error("authorization_declined")]);
    let f = fixture(http);

    f.ash.begin_sign_in().await.unwrap();
    assert_eq!(f.ash.poll_sign_in().await.unwrap_err().kind(), "sign_in_declined");

    // Replaying the same failure forever would be worse than saying plainly
    // that there is nothing to complete.
    assert_eq!(f.ash.poll_sign_in().await.unwrap_err().kind(), "no_sign_in_pending");
}

// ---- Xbox failures ---------------------------------------------------------

#[tokio::test]
async fn each_known_xerr_becomes_its_own_actionable_error() {
    let cases = [
        (2148916233u64, "xbox_no_account"),
        (2148916227, "xbox_banned"),
        (2148916235, "xbox_region_unavailable"),
        (2148916238, "xbox_child_account"),
        (2148916236, "xbox_adult_verification_required"),
        (999, "xbox_other"),
    ];

    for (xerr, expected) in cases {
        let f = fixture(happy_chain().route(XSTS_URL, xsts_denied(xerr)));
        let err = sign_in(&f).await.expect_err("XSTS denied");

        assert_eq!(err.kind(), expected, "XErr {xerr}");
        // None of these are fixed by trying again, and offering a retry for a
        // banned account is worse than offering nothing.
        assert!(!err.is_retryable(), "XErr {xerr} should not offer a retry");
    }
}

// ---- Minecraft services ----------------------------------------------------

#[tokio::test]
async fn a_403_is_the_allow_list_gate_not_a_generic_failure() {
    let f = fixture(happy_chain().route(MC_LOGIN_URL, HttpResponse::status(403)));

    let err = sign_in(&f).await.expect_err("not allow-listed");

    assert_eq!(err.kind(), "not_allow_listed");
    assert!(!err.is_retryable(), "retrying will not add us to the allow list");
    // This is the error the project will actually hit until Mojang answers,
    // so its message must not blame the player's account.
    let message = err.user_message();
    assert!(!message.contains("own"), "must not imply the player lacks a copy: {message}");
}

#[tokio::test]
async fn an_account_with_no_copy_of_the_game_is_told_exactly_that() {
    let f = fixture(happy_chain().route(MC_ENTITLEMENTS_URL, entitlements("")));

    let err = sign_in(&f).await.expect_err("no entitlement");

    assert_eq!(err.kind(), "not_entitled");
    // Only this hop can tell "signed in but owns no copy" from "sign-in
    // failed", which is why the check is mandatory.
    assert!(!err.is_retryable());
}

#[tokio::test]
async fn a_missing_profile_is_game_pass_not_a_missing_purchase() {
    let f = fixture(happy_chain().route(MC_PROFILE_URL, HttpResponse::status(404)));

    let err = sign_in(&f).await.expect_err("no profile");

    assert_eq!(err.kind(), "profile_unavailable");
    // Telling a Game Pass player they do not own the game would be wrong and
    // unactionable; the message has to point at the official launcher.
    assert!(err.user_message().contains("official"), "got: {}", err.user_message());
}

// ---- storage ---------------------------------------------------------------

#[tokio::test]
async fn the_refresh_token_goes_to_the_credential_store_and_not_to_a_file() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    assert_eq!(f.store.keys(), vec!["refresh-token:986dec87b7ec47ff89ff033fdb95c4b5"]);
    assert_eq!(
        f.store.get("refresh-token:986dec87b7ec47ff89ff033fdb95c4b5").unwrap().as_deref(),
        Some("MS-REFRESH")
    );

    // Nothing ash wrote to disk may contain the token.
    let data = std::fs::read_to_string(f.ash.config().data_root.join("accounts.json")).unwrap();
    assert!(!data.contains("MS-REFRESH"), "the token leaked into accounts.json");
    assert!(!data.contains("MC-TOKEN"), "the Minecraft token leaked into accounts.json");
}

#[tokio::test]
async fn signing_in_twice_updates_one_account_rather_than_duplicating_it() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();
    sign_in(&f).await.unwrap();

    let accounts = f.ash.accounts();
    assert_eq!(accounts.accounts.len(), 1, "the profile uuid is the key");
    assert_eq!(accounts.active.as_deref(), Some("986dec87b7ec47ff89ff033fdb95c4b5"));
}

#[tokio::test]
async fn signing_out_erases_the_stored_token() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();
    assert_eq!(f.store.keys().len(), 1);

    let remaining = f.ash.remove_account("986dec87b7ec47ff89ff033fdb95c4b5").unwrap();

    assert!(remaining.accounts.is_empty());
    assert!(remaining.active.is_none());
    // Forgetting the account while leaving a usable credential behind would
    // be worse than not offering sign-out at all.
    assert!(f.store.keys().is_empty(), "a refresh token survived sign-out");
}

#[tokio::test]
async fn a_dead_refresh_token_asks_for_a_fresh_sign_in() {
    let f = fixture(happy_chain());
    sign_in(&f).await.unwrap();

    let rejected =
        happy_chain().route(TOKEN_URL, HttpResponse::json(400, r#"{"error":"invalid_grant"}"#));
    let dead = Ash::new(
        Config::rooted_at(f.ash.config().data_root.parent().unwrap()),
        rejected as Arc<dyn ash_core::http::HttpPort>,
        Arc::clone(&f.store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        CLIENT_ID,
    );

    let err = dead.ensure_session("986dec87b7ec47ff89ff033fdb95c4b5").await.expect_err("dead");

    assert_eq!(err.kind(), "session_expired");
    assert!(err.is_retryable(), "signing in again is a real option");
}

#[tokio::test]
async fn a_session_for_an_unknown_account_is_not_a_crash() {
    let f = fixture(happy_chain());
    let err = f.ash.ensure_session("nobody").await.expect_err("no such account");
    assert_eq!(err.kind(), "session_expired");
}

// ---- the error contract ----------------------------------------------------

#[tokio::test]
async fn no_sign_in_error_leaks_a_url_a_token_or_library_text() {
    let cases: Vec<Arc<FakeHttp>> = vec![
        happy_chain().route(XSTS_URL, xsts_denied(2148916233)),
        happy_chain().route(MC_LOGIN_URL, HttpResponse::status(403)),
        happy_chain().route(MC_ENTITLEMENTS_URL, entitlements("")),
        happy_chain().route(MC_PROFILE_URL, HttpResponse::status(404)),
        happy_chain().route_sequence(TOKEN_URL, vec![token_error("authorization_declined")]),
    ];

    for http in cases {
        let f = fixture(http);
        let err = sign_in(&f).await.expect_err("a failure");
        let message = err.user_message();

        assert!(!message.contains("https://"), "leaked a URL: {message}");
        assert!(!message.contains("MS-ACCESS"), "leaked a token: {message}");
        assert!(!message.contains("MC-TOKEN"), "leaked a token: {message}");
        assert!(!message.contains("XBL-TOKEN"), "leaked a token: {message}");
        assert!(!message.is_empty());
    }
}
