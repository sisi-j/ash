//! The launcher's Wardrobe (spec 0004, step 4), against a fake backend serving
//! ash-backend's own contract examples (`tests/fixtures/backend`).

use std::sync::Arc;

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, AshAccountStatus, Config};

mod common;

const BACKEND: &str = "https://api.ash.test";
const COSMETICS_URL: &str = "https://api.ash.test/v1/cosmetics";
const WARDROBE_URL: &str = "https://api.ash.test/v1/wardrobe";
const CHALLENGE_URL: &str = "https://api.ash.test/v1/session/challenge";
const SESSION_URL: &str = "https://api.ash.test/v1/session";
const JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";
/// The skin URL `common::with_auth_routes` gives the player.
const SKIN_URL: &str = "https://textures/abc";
const PNG: &[u8] = b"\x89PNG pretend";

fn contract(name: &str) -> String {
    std::fs::read_to_string(format!(
        "{}/tests/fixtures/backend/{name}.json",
        env!("CARGO_MANIFEST_DIR")
    ))
    .unwrap()
}

fn texture_url() -> String {
    let catalogue: serde_json::Value = serde_json::from_str(&contract("cosmetics")).unwrap();
    catalogue["cosmetics"][0]["texture_url"].as_str().unwrap().to_owned()
}

/// The backend's session answer, for this test's player, ending far off.
fn session_answer() -> HttpResponse {
    let mut body: serde_json::Value = serde_json::from_str(&contract("session")).unwrap();
    body["account"]["uuid"] = common::PLAYER_UUID.into();
    body["account"]["username"] = common::PLAYER_NAME.into();
    body["session"]["expires_at"] = 99_999_999_999_999u64.into();
    HttpResponse::ok(body.to_string())
}

fn serving() -> Arc<FakeHttp> {
    common::with_auth_routes(FakeHttp::new())
        .route(JOIN_URL, HttpResponse::status(204))
        .route(CHALLENGE_URL, HttpResponse::ok(contract("session-challenge")))
        .route(SESSION_URL, session_answer())
        .route(COSMETICS_URL, HttpResponse::ok(contract("cosmetics")))
        .route(WARDROBE_URL, HttpResponse::ok(contract("wardrobe")))
        .route(texture_url(), HttpResponse::ok(PNG))
        .route(SKIN_URL, HttpResponse::ok(PNG))
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    _tmp: tempfile::TempDir,
}

async fn signed_in(http: Arc<FakeHttp>, to_ash: bool) -> Fixture {
    let tmp = tempfile::tempdir().unwrap();
    let store = InMemoryCredentialStore::new();
    let ash = Ash::new(
        Config { backend_url: BACKEND.into(), ..Config::rooted_at(tmp.path()) },
        Arc::clone(&http) as Arc<dyn HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    ash.begin_sign_in().await.unwrap();
    ash.poll_sign_in().await.unwrap();
    if to_ash {
        store
            .set(
                &format!("ash-session:{}", common::PLAYER_UUID),
                r#"{"token":"T","expires_at":99999999999999}"#,
            )
            .unwrap();
    }
    Fixture { ash, http, _tmp: tmp }
}

const PNG_DATA_URL: &str = "data:image/png;base64,iVBORyBwcmV0ZW5k";

#[tokio::test]
async fn the_wardrobe_shows_the_catalogue_what_is_worn_and_the_player_s_own_skin() {
    let f = signed_in(serving(), true).await;

    let wardrobe = f.ash.wardrobe().await.expect("wardrobe");

    assert!(wardrobe.signed_in);
    assert_eq!(wardrobe.cosmetics.len(), 1);
    assert_eq!(wardrobe.cosmetics[0].id, "cape.ember");
    assert_eq!(wardrobe.cosmetics[0].slot, "cape");
    assert_eq!(
        wardrobe.cosmetics[0].texture, PNG_DATA_URL,
        "a data URL, never a cross-origin request"
    );
    assert_eq!(wardrobe.equipped.cape.as_deref(), Some("cape.ember"));
    assert_eq!(wardrobe.skin.as_deref(), Some(PNG_DATA_URL));
    let wardrobe_request = f.http.everything_sent_to(WARDROBE_URL);
    assert!(wardrobe_request[0].contains("Bearer T"), "{wardrobe_request:?}");
}

#[tokio::test]
async fn opening_the_wardrobe_without_an_ash_session_signs_in_then_and_there() {
    let f = signed_in(serving(), false).await;

    let wardrobe = f.ash.wardrobe().await.expect("wardrobe");

    assert!(wardrobe.signed_in, "signed in on opening, not at the next background try");
    assert_eq!(wardrobe.ash_account, AshAccountStatus::SignedIn);
    assert_eq!(f.http.hits(SESSION_URL), 1);
    assert_eq!(wardrobe.equipped.cape.as_deref(), Some("cape.ember"));
    f.ash.equip("cape", Some("cape.ember")).await.expect("and can wear one");
}

#[tokio::test]
async fn when_ash_can_t_sign_in_the_catalogue_shows_nothing_is_worn_and_the_page_says_why() {
    let refusing = serving().route(
        SESSION_URL,
        HttpResponse::json(503, r#"{"error":{"kind":"mojang_unavailable","message":"x"}}"#),
    );
    let f = signed_in(refusing, false).await;

    let wardrobe = f.ash.wardrobe().await.expect("wardrobe");

    assert!(!wardrobe.signed_in);
    assert_eq!(wardrobe.ash_account, AshAccountStatus::Unreachable);
    assert_eq!(wardrobe.cosmetics.len(), 1);
    assert_eq!(wardrobe.equipped.cape, None);
    assert_eq!(f.http.hits(WARDROBE_URL), 0);
    assert_eq!(
        f.ash.equip("cape", Some("cape.ember")).await.unwrap_err().kind(),
        "session_expired"
    );
}

#[tokio::test]
async fn equipping_and_taking_off_say_which_slot_and_what() {
    let f = signed_in(serving(), true).await;

    // The body of the last PUT: equipping reads the wardrobe again after it.
    let last_put = |f: &Fixture| -> serde_json::Value {
        let sent = f.http.everything_sent_to(WARDROBE_URL);
        let put = sent.iter().rev().find(|r| r.starts_with("Put ")).expect("a PUT").clone();
        serde_json::from_str(put.rsplit('\n').next().unwrap()).unwrap()
    };

    f.ash.equip("cape", Some("cape.ember")).await.expect("equipped");
    assert_eq!(last_put(&f), serde_json::json!({ "slot": "cape", "cosmetic_id": "cape.ember" }));

    f.ash.equip("cape", None).await.expect("taken off");
    assert_eq!(last_put(&f), serde_json::json!({ "slot": "cape", "cosmetic_id": null }));
}

#[tokio::test]
async fn textures_are_fetched_once_and_one_that_fails_leaves_only_that_cosmetic_out() {
    let f = signed_in(serving(), true).await;
    f.ash.wardrobe().await.unwrap();
    f.ash.wardrobe().await.unwrap();
    assert_eq!(f.http.hits(&texture_url()), 1, "immutable at its address, so fetched once");

    let broken = signed_in(serving().route(texture_url(), HttpResponse::status(404)), true).await;
    let wardrobe = broken.ash.wardrobe().await.expect("the page still shows");
    assert!(wardrobe.cosmetics.is_empty());
    assert!(wardrobe.skin.is_some());
}

#[tokio::test]
async fn ash_being_unreachable_is_an_error_the_page_can_show() {
    let f = signed_in(serving().host_unreachable(BACKEND), true).await;
    assert!(f.ash.wardrobe().await.unwrap_err().is_retryable());
}
