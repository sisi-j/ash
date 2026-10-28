fn main() {
    // The beta switch (spec 0006, ADR-0029): `ASH_BETA=1` at build time makes
    // a test build, with the beta phases' work in it. Anything else, or
    // nothing, is a release build. Read here and nowhere else; `ash_state`
    // hands it to ash-core as `Config::beta`.
    println!("cargo::rerun-if-env-changed=ASH_BETA");
    println!("cargo::rustc-check-cfg=cfg(ash_beta)");
    if std::env::var("ASH_BETA").is_ok_and(|value| value == "1") {
        println!("cargo::rustc-cfg=ash_beta");
    }
    tauri_build::build()
}
