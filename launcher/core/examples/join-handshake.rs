//! Prove, live, that Mojang's session server answers ash's join handshake
//! (ADR-0020, research 0010 §1): a join of a server id derived from ash's own
//! challenge, made from a launcher rather than a game connection, is then
//! confirmed by `hasJoined`.
//!
//! Like `allow-list`, this is an example rather than a test so `cargo test`
//! never reaches for the network or waits on a human. Run it yourself:
//!
//!     cargo run -p ash-core --example join-handshake
//!
//! It signs in fresh, by device code, into a temporary data root and an
//! in-memory credential store, so the account ash already has signed in is
//! neither read nor overwritten. It joins no game server and launches
//! nothing. It prints no token: only what Mojang answered, and when.

use std::sync::Arc;
use std::time::{Duration, Instant};

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{HttpPort, HttpRequest, ReqwestHttp};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, SignInStatus};

const CLIENT_ID: &str = "d8cc6384-820e-4608-9a60-f05da43d3571";
const HAS_JOINED: &str = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

#[tokio::main]
async fn main() {
    let tmp = tempfile::tempdir().expect("temp dir");
    let http: Arc<dyn HttpPort> = Arc::new(ReqwestHttp::new());
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        Arc::clone(&http),
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        CLIENT_ID,
    );

    let pending = ash.begin_sign_in().await.unwrap_or_else(|e| fail("starting sign-in", e));
    println!();
    println!("  Open   {}", pending.verification_uri);
    println!("  Enter  {}", pending.user_code);
    println!();
    println!("Waiting for approval...");
    let account = loop {
        match ash.poll_sign_in().await {
            Ok(SignInStatus::Waiting { interval_secs }) => {
                std::thread::sleep(Duration::from_secs(interval_secs))
            }
            Ok(SignInStatus::Complete { account }) => break account,
            Err(e) => fail("signing in", e),
        }
    };
    println!("Signed in as {}.", account.username);

    // What ash's backend will issue: random, and meaningless to anyone else.
    let challenge = uuid::Uuid::new_v4().simple().to_string();
    let joined_at = Instant::now();
    let server_id = ash
        .join_for_ash(&account.profile_id, &challenge)
        .await
        .unwrap_or_else(|e| fail("joining", e));
    println!("Joined server id {server_id}.");

    // A server id nobody joined must not be confirmed; otherwise a "yes"
    // below would prove nothing.
    let control = uuid::Uuid::new_v4().simple().to_string();
    let answer = has_joined(&*http, &account.username, &control).await;
    println!("hasJoined, a server id never joined:   {}", describe(&answer, &account.profile_id));

    // How long a join stays confirmable decides the challenge's lifetime.
    for wait in [0u64, 15, 30, 60, 120] {
        let due = Duration::from_secs(wait);
        if let Some(rest) = due.checked_sub(joined_at.elapsed()) {
            std::thread::sleep(rest);
        }
        let answer = has_joined(&*http, &account.username, &server_id).await;
        println!(
            "hasJoined, ash's server id, at {:>3}s:  {}",
            joined_at.elapsed().as_secs(),
            describe(&answer, &account.profile_id)
        );
    }
}

async fn has_joined(http: &dyn HttpPort, username: &str, server_id: &str) -> (u16, String) {
    let url = format!("{HAS_JOINED}?username={username}&serverId={server_id}");
    match http.send(HttpRequest::get(&url)).await {
        Ok(response) => (response.status, String::from_utf8_lossy(&response.body).into_owned()),
        Err(e) => (0, e.to_string()),
    }
}

fn describe((status, body): &(u16, String), profile_id: &str) -> String {
    match status {
        200 if body.contains(profile_id) => "200, confirmed this profile".into(),
        200 => format!("200, but for another profile: {body}"),
        204 => "204, not joined".into(),
        0 => format!("unreachable: {body}"),
        s => format!("{s}: {body}"),
    }
}

fn fail(step: &str, e: ash_core::AshError) -> ! {
    eprintln!("Failed {step}: {} ({})", e.user_message(), e.kind());
    std::process::exit(1);
}
