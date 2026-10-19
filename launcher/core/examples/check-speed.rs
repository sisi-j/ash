//! How long checking an instance's files takes, on a real depot:
//! `cargo run --release -p ash-core --example check-speed -- <ash data base> <instance id>`.
//! Reads the depot, and writes only what a check writes (`verified.json`).
use std::sync::Arc;
use std::time::Instant;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{HttpPort, ReqwestHttp};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, InstanceId};

#[tokio::main]
async fn main() {
    let mut args = std::env::args().skip(1);
    let base = args.next().expect("the ash data base, e.g. %LOCALAPPDATA%/ash");
    let id: InstanceId =
        serde_json::from_value(serde_json::json!(args.next().expect("an instance id"))).unwrap();
    let ash = Ash::new(
        Config::rooted_at(&base),
        Arc::new(ReqwestHttp::new()) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "check-speed",
    );
    for run in 1..=3 {
        let started = Instant::now();
        let plan = ash.plan_instance(&id).await.expect("a check");
        println!(
            "check {run}: {} ms, {} files, {} missing",
            started.elapsed().as_millis(),
            plan.total_files,
            plan.missing_files
        );
    }
}
