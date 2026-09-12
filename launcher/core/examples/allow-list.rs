//! Ask Mojang, live, whether ash's client id is on the Java Edition API
//! allow list.
//!
//! Every test in this project is fixture-driven and must stay that way. This
//! is the deliberate exception: the allow list is a fact about Mojang's
//! servers on a given day, and no fixture can say which side of it we are on.
//! It is an example rather than a test so `cargo test` never reaches for the
//! network or waits on a human.
//!
//!     cargo run -p ash-core --example allow-list -- <client-id>
//!
//! It drives the real seam, so what it proves is what the launcher will do -
//! not a hand-rolled approximation of the chain. Nothing it does persists: a
//! temporary data root and an in-memory credential store, so a real signed-in
//! account is neither read nor overwritten.

use std::sync::Arc;
use std::time::Duration;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{HttpPort, ReqwestHttp};
use ash_core::{Ash, AshError, Config, SignInStatus};

#[tokio::main]
async fn main() {
    let Some(client_id) = std::env::args().nth(1).or_else(|| std::env::var("ASH_CLIENT_ID").ok())
    else {
        eprintln!("usage: cargo run -p ash-core --example allow-list -- <client-id>");
        std::process::exit(2);
    };

    let tmp = tempfile::tempdir().expect("temp dir");
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        Arc::new(ReqwestHttp::new()) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        client_id,
    );

    let pending = match ash.begin_sign_in().await {
        Ok(pending) => pending,
        Err(e) => {
            verdict(&e);
            std::process::exit(1);
        }
    };

    println!();
    println!("  Open   {}", pending.verification_uri);
    println!("  Enter  {}", pending.user_code);
    println!();
    println!("Waiting for approval...");

    loop {
        match ash.poll_sign_in().await {
            Ok(SignInStatus::Waiting { interval_secs }) => {
                // Blocking is fine here - nothing else runs on this runtime -
                // and it keeps tokio's `time` feature off the dependency list
                // for the sake of one diagnostic.
                std::thread::sleep(Duration::from_secs(interval_secs));
            }
            Ok(SignInStatus::Complete { account }) => {
                println!();
                println!("ALLOW-LISTED. All seven hops completed.");
                println!("  username  {}", account.username);
                println!("  uuid      {}", account.profile_id);
                return;
            }
            Err(e) => {
                verdict(&e);
                std::process::exit(1);
            }
        }
    }
}

/// Turn a failure into an answer about the allow list specifically.
fn verdict(e: &AshError) {
    println!();
    match e.kind() {
        "not_allow_listed" => {
            println!("NOT ALLOW-LISTED.");
            println!();
            println!("Mojang answered 403 at the launcher login hop, which is the");
            println!("allow-list gate and nothing else. This is the answer for this");
            println!("client id, whatever a batch email said.");
        }
        // Both of these come from hops *after* the gate, so reaching them at
        // all proves the client id got through it.
        "not_entitled" => {
            println!("ALLOW-LISTED.");
            println!();
            println!("The login hop succeeded - the gate is open. This account simply");
            println!("does not own Java Edition; run it again with one that does.");
        }
        "profile_unavailable" => {
            println!("ALLOW-LISTED.");
            println!();
            println!("Login and entitlement both succeeded - the gate is open. This");
            println!("account has no Minecraft profile yet.");
        }
        kind => {
            println!("INCONCLUSIVE - failed before reaching the gate.");
            println!();
            println!("  kind   {kind}");
            println!("  says   {}", e.user_message());
        }
    }
}
