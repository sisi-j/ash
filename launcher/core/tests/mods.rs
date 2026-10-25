//! Mods from Modrinth (spec 0005, ADR-0026, research 0014), against a fake
//! Modrinth serving the real answers recorded in `fixtures/modrinth/`. Each
//! file's bytes are made up here and its SHA-512 rewritten to match them, so
//! the hash check is as real as the rest.

use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::FakeProcessPort;
use ash_core::servers::FakeServerPort;
use ash_core::{Ash, Config, InstanceId, Loader};
use serde_json::{json, Value};
use sha2::{Digest, Sha512};

const MODRINTH: &str = "https://modrinth.test/v2";
const IRIS: &str = "YL57xq9U";
const SODIUM: &str = "AANobbMI";
const MODMENU: &str = "mOgUt4GM";
const PLACEHOLDER_API: &str = "eXts2L7r";
const FABRIC_API: &str = "P7dR8mSH";
const LEGACY_FABRIC_API: &str = "9CJED7xi";

fn recorded(name: &str) -> Value {
    let path = format!("{}/tests/fixtures/modrinth/{name}.json", env!("CARGO_MANIFEST_DIR"));
    serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

fn sha512(bytes: &[u8]) -> String {
    Sha512::digest(bytes).iter().map(|b| format!("{b:02x}")).collect()
}

/// A version's files served as made-up bytes, its hashes rewritten to match.
fn with_files(http: &Arc<FakeHttp>, mut version: Value) -> Value {
    for file in version["files"].as_array_mut().unwrap() {
        let bytes = format!("jar {}", file["filename"].as_str().unwrap()).into_bytes();
        file["hashes"]["sha512"] = sha512(&bytes).into();
        http.route(file["url"].as_str().unwrap(), HttpResponse::ok(bytes));
    }
    version
}

fn serve_versions(http: &Arc<FakeHttp>, project: &str, versions: Value) {
    let versions: Vec<Value> =
        versions.as_array().unwrap().iter().cloned().map(|v| with_files(http, v)).collect();
    http.route(
        format!("{MODRINTH}/project/{project}/version"),
        HttpResponse::ok(Value::from(versions).to_string()),
    );
}

fn project(id: &str, title: &str) -> Value {
    json!({ "id": id, "title": title, "icon_url": format!("https://cdn.modrinth.com/data/{id}/icon.png") })
}

/// A made-up version of a made-up mod, for the cases no recorded one shows.
fn version(project: &str, id: &str, kind: &str, dependencies: Value) -> Value {
    json!({
        "id": id, "project_id": project, "version_number": "1.0.0", "version_type": kind,
        "files": [{ "url": format!("https://cdn.modrinth.com/data/{project}/versions/{id}/{project}.jar"),
                    "filename": format!("{project}-1.0.0.jar"), "primary": true,
                    "hashes": { "sha512": "", "sha1": "" }, "size": 1 }],
        "dependencies": dependencies,
    })
}

/// Modrinth as the recorded answers have it: Iris (pinning Sodium 0.8.14),
/// Sodium (newest a beta), Mod Menu (needing Fabric API and Placeholder API)
/// and Legacy Fabric API.
fn modrinth() -> Arc<FakeHttp> {
    let http = FakeHttp::new();
    http.route(
        format!("{MODRINTH}/search"),
        HttpResponse::ok(recorded("search-sodium-1.21.11").to_string()),
    );
    serve_versions(&http, IRIS, recorded("versions-iris-1.21.11"));
    serve_versions(&http, SODIUM, recorded("versions-sodium-1.21.11"));
    serve_versions(&http, MODMENU, recorded("versions-modmenu-1.21.11"));
    serve_versions(&http, LEGACY_FABRIC_API, recorded("versions-legacy-fabric-api-1.8.9"));
    serve_versions(
        &http,
        PLACEHOLDER_API,
        json!([version(PLACEHOLDER_API, "plc1", "release", json!([]))]),
    );
    let pinned = with_files(&http, recorded("versions-sodium-required-by-iris"));
    http.route(format!("{MODRINTH}/version/rkdTcxoT"), HttpResponse::ok(pinned.to_string()));
    let projects = json!([
        project(IRIS, "Iris Shaders"),
        project(SODIUM, "Sodium"),
        project(MODMENU, "Mod Menu"),
        project(PLACEHOLDER_API, "Text Placeholder API"),
        project(FABRIC_API, "Fabric API"),
        project(LEGACY_FABRIC_API, "Legacy Fabric API"),
    ]);
    for p in projects.as_array().unwrap() {
        http.route(
            format!("{MODRINTH}/project/{}", p["id"].as_str().unwrap()),
            HttpResponse::ok(p.to_string()),
        );
    }
    http.route(format!("{MODRINTH}/projects"), HttpResponse::ok(projects.to_string()));
    http
}

struct Fixture {
    ash: Ash,
    http: Arc<FakeHttp>,
    _tmp: tempfile::TempDir,
}

fn fixture(http: Arc<FakeHttp>) -> Fixture {
    let tmp = tempfile::tempdir().unwrap();
    let ash = Ash::new(
        Config { modrinth_url: MODRINTH.into(), ..Config::rooted_at(tmp.path()) },
        Arc::clone(&http) as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        FakeProcessPort::new(),
        FakeServerPort::new(),
        "test-client",
    );
    Fixture { ash, http, _tmp: tmp }
}

impl Fixture {
    fn instance(&self, version: &str, loader: Loader) -> InstanceId {
        self.ash.create_instance("modded", version, loader).expect("instance").id
    }

    fn mods(&self, id: &InstanceId) -> Vec<String> {
        let mut names: Vec<String> = std::fs::read_dir(self.ash.mods_directory(id).unwrap())
            .unwrap()
            .map(|e| e.unwrap().file_name().to_string_lossy().into_owned())
            .collect();
        names.sort();
        names
    }
}

// ---- searching ----------------------------------------------------------------

#[tokio::test]
async fn a_search_asks_only_for_mods_that_fit_the_instance() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);

    let found = f.ash.search_mods(&id, "sodium", 0).await.expect("search");

    assert_eq!(found.total, 45);
    assert_eq!(found.hits[0].title, "Sodium");
    let asked = f.http.everything_sent_to(&format!("{MODRINTH}/search"));
    let decoded =
        asked[0].replace("%5B", "[").replace("%5D", "]").replace("%22", "\"").replace("%3A", ":");
    assert!(decoded.contains(r#"["versions:1.21.11"]"#), "{decoded}");
    assert!(decoded.contains(r#"["categories:fabric"]"#), "{decoded}");
    assert!(
        asked[0].contains("User-Agent: sisi-j/ash/"),
        "Modrinth asks every client to name itself"
    );
    assert!(!asked[0].contains('@'), "no one's address goes to Modrinth");
}

#[tokio::test]
async fn a_1_8_9_instance_searches_legacy_fabric_mods() {
    let f = fixture(modrinth());
    let id = f.instance("1.8.9", Loader::LegacyFabric);

    f.ash.search_mods(&id, "", 0).await.expect("search");

    let asked = f.http.everything_sent_to(&format!("{MODRINTH}/search"))[0].clone();
    assert!(asked.contains("categories%3Alegacy-fabric"), "{asked}");
    assert!(asked.contains("index=downloads"), "nothing typed: the most downloaded first");
}

#[tokio::test]
async fn a_vanilla_instance_is_told_mods_need_fabric() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Vanilla);

    assert_eq!(f.ash.search_mods(&id, "x", 0).await.unwrap_err().kind(), "mods_need_fabric");
    assert_eq!(f.ash.install_mod(&id, IRIS).await.unwrap_err().kind(), "mods_need_fabric");
}

// ---- installing ------------------------------------------------------------------

#[tokio::test]
async fn installing_takes_what_the_mod_requires_and_the_exact_version_it_pins() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);

    let outcome = f.ash.install_mod(&id, IRIS).await.expect("installed");

    let installed: Vec<(&str, &str)> =
        outcome.installed.iter().map(|m| (m.title.as_str(), m.version_number.as_str())).collect();
    assert_eq!(
        installed,
        [("Iris Shaders", "1.10.8+1.21.11-fabric"), ("Sodium", "mc1.21.11-0.8.14-fabric")]
    );
    assert_eq!(
        f.mods(&id),
        ["iris-fabric-1.10.8+mc1.21.11.jar", "sodium-fabric-0.8.14+mc1.21.11.jar"]
    );
    assert_eq!(f.ash.installed_mods(&id).unwrap(), outcome.installed, "recorded in ash-mods.json");
}

#[tokio::test]
async fn installing_turns_the_instance_s_mods_on_and_says_so() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    assert!(!f.ash.overrides(&id).unwrap().third_party_mods, "off, as every instance starts");

    let first = f.ash.install_mod(&id, SODIUM).await.unwrap();
    assert!(first.mods_turned_on);
    assert!(f.ash.overrides(&id).unwrap().third_party_mods);

    let second = f.ash.install_mod(&id, IRIS).await.unwrap();
    assert!(!second.mods_turned_on, "already on");
}

#[tokio::test]
async fn the_newest_release_is_chosen_over_a_newer_beta() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);

    let outcome = f.ash.install_mod(&id, SODIUM).await.unwrap();

    assert_eq!(outcome.installed[0].version_number, "mc1.21.11-0.8.14-fabric");
    assert_eq!(outcome.installed[0].version_type, "release");
}

#[tokio::test]
async fn fabric_api_is_ash_s_own_and_never_installed_twice() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);

    let outcome = f.ash.install_mod(&id, MODMENU).await.unwrap();

    let titles: Vec<&str> = outcome.installed.iter().map(|m| m.title.as_str()).collect();
    assert_eq!(titles, ["Mod Menu", "Text Placeholder API"]);
    assert_eq!(f.http.hits(&format!("{MODRINTH}/project/{FABRIC_API}/version")), 0);
}

#[tokio::test]
async fn on_1_8_9_legacy_fabric_api_is_installed_whole_since_ash_ships_four_of_its_modules() {
    let http = modrinth();
    serve_versions(
        &http,
        "legacymod",
        json!([version(
            "legacymod",
            "lm1",
            "release",
            json!([{ "project_id": LEGACY_FABRIC_API, "version_id": null, "dependency_type": "required" }])
        )]),
    );
    http.route(
        format!("{MODRINTH}/project/legacymod"),
        HttpResponse::ok(project("legacymod", "A 1.8.9 Mod").to_string()),
    );
    let f = fixture(http);
    let id = f.instance("1.8.9", Loader::LegacyFabric);

    f.ash.install_mod(&id, "legacymod").await.unwrap();

    assert_eq!(f.mods(&id), ["legacy-fabric-api-1.13.5.jar", "legacymod-1.0.0.jar"]);
}

#[tokio::test]
async fn a_file_that_doesn_t_match_modrinth_s_hash_never_reaches_the_folder() {
    let http = modrinth();
    let mut versions = recorded("versions-sodium-1.21.11");
    for v in versions.as_array_mut().unwrap() {
        let v2 = with_files(&http, v.clone());
        *v = v2;
        v["files"][0]["hashes"]["sha512"] = "00".repeat(64).into();
    }
    http.route(
        format!("{MODRINTH}/project/{SODIUM}/version"),
        HttpResponse::ok(versions.to_string()),
    );
    let f = fixture(http);
    let id = f.instance("1.21.11", Loader::Fabric);

    let err = f.ash.install_mod(&id, SODIUM).await.unwrap_err();

    assert_eq!(err.kind(), "verification_failed");
    assert!(f.mods(&id).is_empty(), "nothing left behind: {:?}", f.mods(&id));
    assert!(f.ash.installed_mods(&id).unwrap().is_empty());
}

#[tokio::test]
async fn a_mod_that_declares_itself_incompatible_with_one_installed_is_refused_by_name() {
    let http = modrinth();
    serve_versions(
        &http,
        "clash",
        json!([version(
            "clash",
            "c1",
            "release",
            json!([{ "project_id": SODIUM, "version_id": null, "dependency_type": "incompatible" }])
        )]),
    );
    http.route(
        format!("{MODRINTH}/project/clash"),
        HttpResponse::ok(project("clash", "Clashing Renderer").to_string()),
    );
    let f = fixture(http);
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, SODIUM).await.unwrap();

    let err = f.ash.install_mod(&id, "clash").await.unwrap_err();

    assert_eq!(err.kind(), "mod_incompatible");
    assert!(
        err.user_message().contains("Clashing Renderer") && err.user_message().contains("Sodium"),
        "{}",
        err.user_message()
    );
    assert_eq!(f.mods(&id), ["sodium-fabric-0.8.14+mc1.21.11.jar"]);
}

#[tokio::test]
async fn a_mod_with_no_version_for_the_instance_says_so_by_name() {
    let http = modrinth();
    http.route(format!("{MODRINTH}/project/{IRIS}/version"), HttpResponse::ok("[]"));
    let f = fixture(http);
    let id = f.instance("1.21.11", Loader::Fabric);

    let err = f.ash.install_mod(&id, IRIS).await.unwrap_err();

    assert_eq!(err.kind(), "mod_unavailable");
    assert!(err.user_message().contains("Iris Shaders"), "{}", err.user_message());
}

#[tokio::test]
async fn installed_mods_are_marked_in_a_search() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, SODIUM).await.unwrap();

    let found = f.ash.search_mods(&id, "sodium", 0).await.unwrap();

    let sodium = found.hits.iter().find(|h| h.project_id == SODIUM).unwrap();
    assert!(sodium.installed);
    assert!(found.hits.iter().filter(|h| h.project_id != SODIUM).all(|h| !h.installed));
}

// ---- what's in the folder (#146) ------------------------------------------------

fn jar(name: &str) -> Vec<u8> {
    std::fs::read(format!("{}/tests/fixtures/jars/{name}", env!("CARGO_MANIFEST_DIR"))).unwrap()
}

#[tokio::test]
async fn the_list_names_every_jar_from_ash_s_record_or_the_jar_s_own_metadata() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, SODIUM).await.unwrap();
    let mods = f.ash.mods_directory(&id).unwrap();
    std::fs::write(mods.join("handmod-2.3.4.jar"), jar("hand-added.jar")).unwrap();
    std::fs::write(mods.join("mystery.jar"), jar("no-metadata.jar")).unwrap();
    std::fs::write(mods.join("notes.txt"), "not a mod").unwrap();

    let listed = f.ash.list_mods(&id).unwrap();

    let shown: Vec<(&str, Option<&str>, bool)> =
        listed.iter().map(|m| (m.title.as_str(), m.version.as_deref(), m.from_modrinth)).collect();
    assert_eq!(
        shown,
        [
            ("Hand Mod", Some("2.3.4"), false),
            ("mystery", None, false),
            ("Sodium", Some("mc1.21.11-0.8.14-fabric"), true)
        ]
    );
    assert!(listed.iter().all(|m| m.enabled));
}

#[tokio::test]
async fn a_mod_turns_off_and_on_by_the_rename_other_launchers_use() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, SODIUM).await.unwrap();
    let file = "sodium-fabric-0.8.14+mc1.21.11.jar";

    let off = f.ash.set_mod_enabled(&id, file, false).unwrap();
    assert!(!off[0].enabled);
    assert_eq!(f.mods(&id), [format!("{file}.disabled")]);
    assert_eq!(off[0].title, "Sodium", "still known by name while off");

    let on = f.ash.set_mod_enabled(&id, file, true).unwrap();
    assert!(on[0].enabled);
    assert_eq!(f.mods(&id), [file]);
}

#[tokio::test]
async fn removing_a_mod_takes_its_file_and_ash_s_record_of_it() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, IRIS).await.unwrap();
    f.ash.set_mod_enabled(&id, "iris-fabric-1.10.8+mc1.21.11.jar", false).unwrap();

    let left = f.ash.remove_mod(&id, "iris-fabric-1.10.8+mc1.21.11.jar").unwrap();

    assert_eq!(left.iter().map(|m| m.title.as_str()).collect::<Vec<_>>(), ["Sodium"]);
    assert_eq!(f.mods(&id), ["sodium-fabric-0.8.14+mc1.21.11.jar"]);
    assert_eq!(f.ash.installed_mods(&id).unwrap().len(), 1);
}

#[tokio::test]
async fn a_mod_that_does_what_an_ash_feature_does_says_which() {
    let http = modrinth();
    let freelook = "g4pR0Fmy";
    serve_versions(&http, freelook, json!([version(freelook, "fl1", "release", json!([]))]));
    http.route(
        format!("{MODRINTH}/project/{freelook}"),
        HttpResponse::ok(project(freelook, "Freelook").to_string()),
    );
    let f = fixture(http);
    let id = f.instance("1.21.11", Loader::Fabric);
    f.ash.install_mod(&id, freelook).await.unwrap();
    f.ash.install_mod(&id, SODIUM).await.unwrap();

    let listed = f.ash.list_mods(&id).unwrap();

    let notes: Vec<(&str, Option<&str>)> =
        listed.iter().map(|m| (m.title.as_str(), m.overlaps.as_deref())).collect();
    assert_eq!(notes, [("Freelook", Some("Freelook")), ("Sodium", None)]);
}

#[tokio::test]
async fn a_name_that_reaches_outside_the_folder_is_refused() {
    let f = fixture(modrinth());
    let id = f.instance("1.21.11", Loader::Fabric);
    let outside = f.ash.mods_directory(&id).unwrap().parent().unwrap().join("options.txt");
    std::fs::write(&outside, "keep me").unwrap();

    for name in ["../options.txt", r"..\options.txt", r"C:\Windows\x.jar", "../x.jar"] {
        assert!(f.ash.remove_mod(&id, name).is_err(), "{name}");
        assert!(f.ash.set_mod_enabled(&id, name, false).is_err(), "{name}");
    }
    assert!(outside.exists());
}
