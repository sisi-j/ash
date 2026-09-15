//! Several accounts on one machine, and which of them a launch uses.
//!
//! The point of this ticket is that a player never joins a server as the
//! wrong person, so the load-bearing assertion is not "the UI shows the right
//! name" - it is which *credential* ash presents when it starts the game.

use std::sync::Arc;

use ash_core::credentials::{CredentialStore, InMemoryCredentialStore};
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{Ash, Cancel, Config, NullSink, VERSION_MANIFEST_URL};

mod common;

const VERSION: &str = "1.21.11";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.11.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const CLIENT_JAR: &[u8] = b"pretend this is the client jar";

/// Two players, as Mojang would describe them.
const ALICE: (&str, &str) = ("11111111111111111111111111111111", "alice");
const BOB: (&str, &str) = ("22222222222222222222222222222222", "bob");

/// Microsoft hands back a different refresh token per account, and which one
/// ash later presents is the whole question this file exists to answer.
const ALICE_REFRESH: &str = "MS-REFRESH-ALICE";
const BOB_REFRESH: &str = "MS-REFRESH-BOB";

// ---- fixtures --------------------------------------------------------------

fn profile(player: (&str, &str), skin: Option<&str>) -> HttpResponse {
    let skins = match skin {
        Some(url) => format!(r#""skins":[{{"state":"ACTIVE","url":"{url}"}}]"#),
        None => r#""skins":[]"#.to_owned(),
    };
    HttpResponse::ok(format!(r#"{{"id":"{}","name":"{}",{skins}}}"#, player.0, player.1))
}

fn token(refresh: &str) -> HttpResponse {
    HttpResponse::ok(format!(r#"{{"access_token":"MS-ACCESS","refresh_token":"{refresh}"}}"#))
}

/// A version with no libraries and no assets: these tests are about accounts,
/// not about the depot.
fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
        "downloads":{{"client":{{"sha1":"{}","size":{},"url":"{CLIENT_URL}"}}}},
        "libraries":[],
        "arguments":{{"jvm":["-cp","${{classpath}}"],
                      "game":["--username","${{auth_player_name}}",
                              "--uuid","${{auth_uuid}}"]}}}}"#,
        common::sha1(CLIENT_JAR),
        CLIENT_JAR.len()
    )
}

fn manifest_json() -> String {
    format!(
        r#"{{"latest":{{"release":"{VERSION}","snapshot":"{VERSION}"}},"versions":[
            {{"id":"{VERSION}","type":"release","releaseTime":"2026-01-05T09:00:00+00:00",
              "url":"{VERSION_URL}","sha1":"{}"}}
        ]}}"#,
        common::sha1(version_json().as_bytes())
    )
}

/// The sign-in chain, serving Alice first and Bob second.
///
/// The last response of a sequence repeats, so any later refresh is answered
/// as Bob. That is fine and deliberate: these tests assert on the credential
/// ash *sends*, which is the thing that decides who the player actually is.
fn serving() -> Arc<FakeHttp> {
    common::with_runtime_routes(common::with_auth_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(VERSION_URL, HttpResponse::ok(version_json()))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR)),
    ))
    .route_sequence(
        common::MC_PROFILE_URL,
        vec![profile(ALICE, Some("https://textures/alice")), profile(BOB, None)],
    )
    .route_sequence(common::TOKEN_URL, vec![token(ALICE_REFRESH), token(BOB_REFRESH)])
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    store: Arc<InMemoryCredentialStore>,
    _tmp: tempfile::TempDir,
}

fn fixture() -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let http = serving();
    let store = InMemoryCredentialStore::new();
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        Arc::clone(&http) as Arc<dyn HttpPort>,
        Arc::clone(&store) as Arc<dyn CredentialStore>,
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "test-client",
    );
    Fixture { ash, http, store, _tmp: tmp }
}

impl Fixture {
    /// Walk one whole sign-in. Each call takes the next player.
    async fn sign_in(&self) {
        self.ash.begin_sign_in().await.expect("device code");
        self.ash.poll_sign_in().await.expect("sign-in");
    }

    async fn sign_in_both(&self) {
        self.sign_in().await;
        self.sign_in().await;
    }
}

fn names(accounts: &ash_core::Accounts) -> Vec<&str> {
    accounts.accounts.iter().map(|a| a.username.as_str()).collect()
}

// ---- more than one account --------------------------------------------------

#[tokio::test]
async fn two_microsoft_accounts_can_be_signed_in_at_once() {
    let f = fixture();

    f.sign_in_both().await;

    let accounts = f.ash.accounts();
    assert_eq!(names(&accounts), ["alice", "bob"]);
    // Keyed by profile UUID per ADR-0009, so two different players are two
    // accounts however similar their names.
    assert_eq!(accounts.accounts[0].profile_id, ALICE.0);
    assert_eq!(accounts.accounts[1].profile_id, BOB.0);
}

#[tokio::test]
async fn the_account_just_signed_in_becomes_the_active_one() {
    let f = fixture();

    f.sign_in().await;
    assert_eq!(f.ash.accounts().active.as_deref(), Some(ALICE.0));

    f.sign_in().await;

    // Signing in is the player expressing an intent about who they are.
    assert_eq!(f.ash.accounts().active.as_deref(), Some(BOB.0));
}

#[tokio::test]
async fn the_order_accounts_were_added_in_is_kept() {
    let f = fixture();
    f.sign_in_both().await;

    // A list that reorders itself between renders is a list a player cannot
    // click on with confidence.
    for _ in 0..3 {
        assert_eq!(names(&f.ash.accounts()), ["alice", "bob"]);
    }
}

#[tokio::test]
async fn every_account_carries_what_the_ui_needs_to_tell_them_apart() {
    let f = fixture();
    f.sign_in_both().await;

    let accounts = f.ash.accounts();

    // A player should never have to read a UUID to know which account is
    // which.
    assert_eq!(accounts.accounts[0].username, "alice");
    assert_eq!(accounts.accounts[0].skin_url.as_deref(), Some("https://textures/alice"));

    // A player with no skin set still has a name, and the UI has to cope
    // with the face being absent rather than assuming one.
    assert_eq!(accounts.accounts[1].username, "bob");
    assert_eq!(accounts.accounts[1].skin_url, None);
}

// ---- choosing ---------------------------------------------------------------

#[tokio::test]
async fn the_active_account_can_be_changed() {
    let f = fixture();
    f.sign_in_both().await;

    let accounts = f.ash.select_account(ALICE.0).expect("select");

    assert_eq!(accounts.active.as_deref(), Some(ALICE.0));
    assert_eq!(accounts.active_account().map(|a| a.username.as_str()), Some("alice"));
    // Switching who plays is not the same as forgetting anyone.
    assert_eq!(names(&accounts), ["alice", "bob"]);
}

#[tokio::test]
async fn selecting_an_account_ash_does_not_have_says_exactly_that() {
    let f = fixture();
    f.sign_in().await;

    let err = f.ash.select_account(BOB.0).expect_err("bob never signed in");

    // Not "your session expired": nothing expired, and sending the player
    // off to sign in again would be fixing something that is not broken.
    assert_eq!(err.kind(), "account_not_found");
    assert!(!err.user_message().contains(BOB.0), "leaked a uuid: {}", err.user_message());
}

#[tokio::test]
async fn launching_presents_the_credential_of_the_chosen_account() {
    let f = fixture();
    f.sign_in_both().await;
    f.ash.select_account(ALICE.0).expect("select alice");
    let instance = f.ash.create_instance("modern", VERSION).expect("instance");

    f.ash.launch(&instance.id, &NullSink, &Cancel::new()).await.expect("launch");

    // This is the whole ticket. Bob signed in most recently, so anything
    // that reaches for "the newest account" rather than "the chosen one"
    // would send Bob's refresh token and put Bob on the server.
    let sent = f.http.last_body(common::TOKEN_URL).expect("a token request");
    assert!(sent.contains(ALICE_REFRESH), "launched with the wrong account's credential");
    assert!(!sent.contains(BOB_REFRESH));
}

#[tokio::test]
async fn switching_accounts_switches_who_the_next_launch_is() {
    let f = fixture();
    f.sign_in_both().await;
    let instance = f.ash.create_instance("modern", VERSION).expect("instance");

    f.ash.select_account(ALICE.0).expect("select alice");
    f.ash.launch(&instance.id, &NullSink, &Cancel::new()).await.expect("launch");
    let first = f.http.last_body(common::TOKEN_URL).expect("a token request");

    f.ash.stop_game(&instance.id);
    f.ash.select_account(BOB.0).expect("select bob");
    f.ash.launch(&instance.id, &NullSink, &Cancel::new()).await.expect("launch");
    let second = f.http.last_body(common::TOKEN_URL).expect("a token request");

    assert!(first.contains(ALICE_REFRESH));
    assert!(second.contains(BOB_REFRESH));
}

// ---- signing out ------------------------------------------------------------

#[tokio::test]
async fn signing_one_account_out_leaves_the_other_signed_in() {
    let f = fixture();
    f.sign_in_both().await;

    let accounts = f.ash.remove_account(ALICE.0).expect("remove");

    assert_eq!(names(&accounts), ["bob"]);
    // Erasing the wrong player's credential would sign out someone who
    // asked for nothing.
    let keys = f.store.keys();
    assert!(keys.iter().any(|k| k.contains(BOB.0)), "bob's token was taken too: {keys:?}");
    assert!(!keys.iter().any(|k| k.contains(ALICE.0)), "alice's token survived: {keys:?}");
}

#[tokio::test]
async fn signing_out_the_active_account_hands_over_to_another() {
    let f = fixture();
    f.sign_in_both().await;
    assert_eq!(f.ash.accounts().active.as_deref(), Some(BOB.0));

    let accounts = f.ash.remove_account(BOB.0).expect("remove");

    // Leaving `active` pointing at a removed account would mean the next
    // launch failed with "no account" while one is clearly signed in.
    assert_eq!(accounts.active.as_deref(), Some(ALICE.0));
    assert!(accounts.active_account().is_some());
}

#[tokio::test]
async fn signing_out_the_last_account_leaves_nobody_active() {
    let f = fixture();
    f.sign_in().await;

    let accounts = f.ash.remove_account(ALICE.0).expect("remove");

    assert!(accounts.accounts.is_empty());
    assert_eq!(accounts.active, None);
    assert!(f.store.keys().is_empty(), "a credential outlived the account that owned it");
}

#[tokio::test]
async fn a_signed_out_account_cannot_launch_on_a_leftover_token() {
    let f = fixture();
    f.sign_in_both().await;
    let instance = f.ash.create_instance("modern", VERSION).expect("instance");
    f.ash.remove_account(BOB.0).expect("remove bob");
    f.ash.select_account(ALICE.0).expect("select alice");

    f.ash.launch(&instance.id, &NullSink, &Cancel::new()).await.expect("launch");

    let sent = f.http.last_body(common::TOKEN_URL).expect("a token request");
    assert!(sent.contains(ALICE_REFRESH));
    assert!(!sent.contains(BOB_REFRESH), "a signed-out account's credential was still used");
}
