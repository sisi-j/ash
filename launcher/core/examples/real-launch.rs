//! Start the real game, on this machine, against Mojang's real services.
//!
//! The launch tests assert the *recorded* invocation, and they have to: a
//! test that opened a game window is not a test. This is the other half -
//! the manual acceptance - and it is an example for the same reason
//! `allow-list.rs` is: it needs a Microsoft account and a human at a
//! browser.
//!
//!     cargo run -p ash-core --example real-launch -- <client-id> [version] [loader]
//!
//! `loader` is `vanilla` (the default) or `fabric`. A modded run is the only
//! way to see the acceptance criteria that no fixture can stand in for: the
//! game reaching its main menu with the loader running, and Fabric Loader and
//! Fabric API appearing in the game's own mod list.
//!
//! It uses ash's real data directory and the real OS credential store, so
//! this is the product rather than a simulation of it, and a sign-in here is
//! the same sign-in the launcher sees.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use ash_core::credentials::OsCredentialStore;
use ash_core::http::{HttpPort, ReqwestHttp};
use ash_core::process::{GameStatus, OsProcessPort};
use ash_core::{
    Ash, Cancel, Config, Loader, PrepareEvent, ProgressSink, SignInStatus, VersionKind,
};

struct Printer;

impl ProgressSink for Printer {
    fn emit(&self, event: PrepareEvent) {
        match event {
            PrepareEvent::Resolving { version_id } => println!("  resolving {version_id}"),
            PrepareEvent::Planned { missing_files, missing_bytes, already_present, .. } => {
                println!(
                    "  {missing_files} to fetch ({:.1} MB), {already_present} already here",
                    missing_bytes as f64 / 1_048_576.0
                );
            }
            // One line per file would be thousands of lines; every 250 is
            // enough to see it moving.
            PrepareEvent::Downloaded { done_files, done_bytes, .. } if done_files % 250 == 0 => {
                println!("  {done_files} files, {:.1} MB", done_bytes as f64 / 1_048_576.0);
            }
            PrepareEvent::Runtime { component } => println!("  java runtime: {component}"),
            PrepareEvent::Reverifying { path } => println!("  re-fetching {path}"),
            PrepareEvent::Done { version_id } => println!("  {version_id} is ready"),
            _ => {}
        }
    }
}

#[tokio::main]
async fn main() {
    let mut args = std::env::args().skip(1);
    let Some(client_id) = args.next() else {
        eprintln!(
            "usage: cargo run -p ash-core --example real-launch -- <client-id> [version] [loader]"
        );
        std::process::exit(2);
    };
    let wanted = args.next();
    let loader = match args.next().as_deref() {
        None | Some("vanilla") => Loader::Vanilla,
        Some("fabric") => Loader::Fabric,
        Some(other) => {
            eprintln!("unknown loader {other}; expected vanilla or fabric");
            std::process::exit(2);
        }
    };

    let ash = Ash::new(
        Config::rooted_at(data_dir()),
        Arc::new(ReqwestHttp::new()) as Arc<dyn HttpPort>,
        Arc::new(OsCredentialStore::new()),
        Arc::new(OsProcessPort::new()),
        client_id,
    );

    // ---- an account ----
    if ash.accounts().active_account().is_none() {
        let pending = ash.begin_sign_in().await.expect("device code");
        println!();
        println!("  Open   {}", pending.verification_uri);
        println!("  Enter  {}", pending.user_code);
        println!();
        loop {
            match ash.poll_sign_in().await.expect("sign-in") {
                SignInStatus::Waiting { interval_secs } => {
                    std::thread::sleep(Duration::from_secs(interval_secs));
                }
                SignInStatus::Complete { account } => {
                    println!("signed in as {}", account.username);
                    break;
                }
            }
        }
    }
    let accounts = ash.accounts();
    let account = accounts.active_account().expect("an account");
    println!("playing as {}", account.username);

    // ---- a version ----
    let catalogue = ash.catalogue().await.expect("catalogue");
    let version = match wanted {
        Some(id) => id,
        None => catalogue
            .versions
            .iter()
            .find(|v| v.kind == VersionKind::Release && v.id.starts_with("1.21"))
            .map(|v| v.id.clone())
            .expect("a 1.21.x release"),
    };
    println!("version {version}");

    // ---- an instance ----
    let make = || match ash.create_instance("real-launch", &version, loader) {
        Ok(instance) => instance,
        Err(e) => {
            println!("FAILED ({}): {}", e.kind(), e.user_message());
            std::process::exit(1);
        }
    };
    let instances = ash.instances().expect("instances");
    let instance = match instances.into_iter().find(|i| i.name == "real-launch") {
        // The loader is fixed at creation, so switching one means a new
        // instance rather than an edit.
        Some(existing) if existing.version_id == version && existing.loader == loader => existing,
        Some(existing) => {
            ash.delete_instance(&existing.id).expect("replacing the old one");
            make()
        }
        None => make(),
    };
    println!("instance {} ({:?})", instance.id, instance.loader);

    // ---- prepare and launch ----
    println!("preparing (this is the slow part on a cold depot)");
    let cancel = Cancel::new();

    let invocation = match ash.launch(&instance.id, &Printer, &cancel).await {
        Ok(invocation) => invocation,
        Err(e) => {
            println!();
            println!("FAILED ({}): {}", e.kind(), e.user_message());
            std::process::exit(1);
        }
    };

    println!();
    println!("{}", invocation.program);
    for arg in &invocation.args {
        println!("  {arg}");
    }
    println!();

    // ---- watch it ----
    println!("started; watching for 90 seconds");
    for _ in 0..30 {
        std::thread::sleep(Duration::from_secs(3));
        match ash.game_status(&instance.id) {
            Some(GameStatus::Running) => {}
            Some(GameStatus::Exited { code, clean }) => {
                println!();
                if clean {
                    println!("the game exited cleanly.");
                } else {
                    println!("the game exited with {code:?}. Last output:");
                    for line in ash.game_log(&instance.id).iter().rev().take(40).rev() {
                        println!("  {line}");
                    }
                }
                std::process::exit(i32::from(!clean));
            }
            None => unreachable!("it was just launched"),
        }
    }

    println!();
    println!("STILL RUNNING after 90 seconds - the game is up.");
    for line in ash.game_log(&instance.id).iter().take(20) {
        println!("  {line}");
    }
    ash.stop_game(&instance.id);
}

/// The same directory the Tauri adapter picks, so this shares one depot with
/// the launcher rather than downloading its own copy of everything.
fn data_dir() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local/share")))
        .unwrap_or_else(std::env::temp_dir)
        .join("ash")
}
