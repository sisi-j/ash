//! Time what ash does when the window opens.
//!
//! "The app opens in a couple of seconds" is a claim about a real machine
//! with a real depot in it, so this runs against ash's actual data
//! directory rather than a fixture. It reads and never writes.
//!
//!     cargo run --release -p ash-core --example startup
//!
//! Release, because the interesting number here is SHA-1 throughput over
//! several hundred megabytes and a debug build would be measuring the wrong
//! thing entirely.

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Instant;

use ash_core::credentials::OsCredentialStore;
use ash_core::http::{HttpPort, ReqwestHttp};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{Ash, Config};

#[tokio::main]
async fn main() {
    let ash = Ash::new(
        Config::rooted_at(data_dir()),
        Arc::new(ReqwestHttp::new()) as Arc<dyn HttpPort>,
        Arc::new(OsCredentialStore::new()),
        // Nothing here launches anything; the fake makes that structural.
        FakeProcessPort::new() as Arc<dyn ProcessPort>,
        "startup-timing",
    );

    // What App.tsx asks for the moment it mounts.
    let accounts = time("accounts", || ash.accounts());
    println!("      {} signed in", accounts.accounts.len());

    let instances = time("instances", || ash.instances().expect("instances"));
    println!("      {} instances", instances.len());

    let started = Instant::now();
    let catalogue = ash.catalogue().await.expect("catalogue");
    report("catalogue", started);
    println!("      {} versions, from {:?}", catalogue.versions.len(), catalogue.source);

    // And then the selected instance's panel plans it.
    let Some(instance) = instances.first() else {
        println!("\nno instances, so there is nothing to plan");
        return;
    };
    println!("\nplanning {} ({})", instance.name, instance.version_id);
    let started = Instant::now();
    let plan = ash.plan_instance(&instance.id).await.expect("plan");
    report("plan_instance", started);
    println!("      {} files, {} missing", plan.total_files, plan.missing_files);
}

fn time<T>(what: &str, f: impl FnOnce() -> T) -> T {
    let started = Instant::now();
    let value = f();
    report(what, started);
    value
}

fn report(what: &str, started: Instant) {
    println!("{what:>16}  {:>8.0} ms", started.elapsed().as_secs_f64() * 1000.0);
}

fn data_dir() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".local/share")))
        .unwrap_or_else(std::env::temp_dir)
        .join("ash")
}
