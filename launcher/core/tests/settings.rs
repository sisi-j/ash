//! Instance settings, and the line machine-local values must not cross.
//!
//! Phase 4 syncs an account's launcher state between machines. The failure
//! this file exists to prevent is a memory figure from a 32GB desktop
//! arriving on an 8GB laptop, so the tests below do not check that a comment
//! says the right thing - they check that the value is not in the directory
//! a sync would carry, and that the instance definition has nowhere to put
//! it.

use std::path::PathBuf;
use std::sync::Arc;

use ash_core::credentials::InMemoryCredentialStore;
use ash_core::http::{FakeHttp, HttpPort, HttpResponse};
use ash_core::process::{FakeProcessPort, ProcessPort};
use ash_core::{
    Ash, Cancel, Config, InstanceId, MachineOverrides, NullSink, Resolution,
    DEFAULT_MEMORY_MB, VERSION_MANIFEST_URL,
};

mod common;

const VERSION: &str = "1.21.11";
const VERSION_URL: &str = "https://piston-meta.mojang.com/v1/packages/aa/1.21.11.json";
const CLIENT_URL: &str = "https://piston-data.mojang.com/v1/objects/cc/client.jar";
const CLIENT_JAR: &[u8] = b"pretend this is the client jar";

/// A memory figure no default would ever produce, so finding it anywhere is
/// proof it came from the override and not from coincidence.
const DESKTOP_MEMORY_MB: u32 = 24576;

fn version_json() -> String {
    format!(
        r#"{{"id":"{VERSION}","type":"release",
        "mainClass":"net.minecraft.client.main.Main",
        "javaVersion":{{"component":"java-runtime-delta","majorVersion":21}},
        "downloads":{{"client":{{"sha1":"{}","size":{},"url":"{CLIENT_URL}"}}}},
        "libraries":[],
        "arguments":{{"jvm":["-cp","${{classpath}}"],
                      "game":["--username","${{auth_player_name}}"]}}}}"#,
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

struct Fixture {
    ash: Ash,
    process: Arc<FakeProcessPort>,
    tmp: tempfile::TempDir,
}

fn fixture() -> Fixture {
    let tmp = tempfile::tempdir().expect("temp dir");
    let http = common::with_runtime_routes(common::with_auth_routes(
        FakeHttp::new()
            .route(VERSION_MANIFEST_URL, HttpResponse::ok(manifest_json()))
            .route(VERSION_URL, HttpResponse::ok(version_json()))
            .route(CLIENT_URL, HttpResponse::ok(CLIENT_JAR)),
    ));
    let process = FakeProcessPort::new();
    let ash = Ash::new(
        Config::rooted_at(tmp.path()),
        http as Arc<dyn HttpPort>,
        InMemoryCredentialStore::new(),
        Arc::clone(&process) as Arc<dyn ProcessPort>,
        "test-client",
    );
    Fixture { ash, process, tmp }
}

impl Fixture {
    async fn ready(&self) -> InstanceId {
        self.ash.begin_sign_in().await.expect("device code");
        self.ash.poll_sign_in().await.expect("sign-in");
        self.ash.create_instance("modern", VERSION).expect("instance").id
    }

    async fn launch(&self, id: &InstanceId) -> Vec<String> {
        self.ash.launch(id, &NullSink, &Cancel::new()).await.expect("launch");
        self.process.last().args
    }
}

fn value_of<'a>(args: &'a [String], flag: &str) -> Option<&'a str> {
    let at = args.iter().position(|a| a == flag)?;
    args.get(at + 1).map(String::as_str)
}

/// Every file under a directory, read as text.
fn files_under(root: &std::path::Path) -> Vec<(PathBuf, String)> {
    let mut found = Vec::new();
    let mut stack = vec![root.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if let Ok(text) = std::fs::read_to_string(&path) {
                found.push((path, text));
            }
        }
    }
    found
}

// ---- memory ------------------------------------------------------------------

#[tokio::test]
async fn memory_reaches_the_jvm() {
    let f = fixture();
    let id = f.ready().await;

    f.ash
        .set_overrides(&id, MachineOverrides { memory_mb: Some(8192), ..Default::default() })
        .expect("set");

    let args = f.launch(&id).await;
    assert!(args.contains(&"-Xmx8192M".to_owned()), "no heap size on the command line: {args:?}");
}

#[tokio::test]
async fn an_instance_nobody_has_tuned_still_gets_a_heap_size() {
    let f = fixture();
    let id = f.ready().await;

    let args = f.launch(&id).await;

    // Mojang's metadata never states one, so leaving it out would hand the
    // game whatever fraction of RAM the JVM felt like.
    assert!(args.contains(&format!("-Xmx{DEFAULT_MEMORY_MB}M")));
}

#[tokio::test]
async fn memory_the_jvm_would_refuse_is_refused_first() {
    let f = fixture();
    let id = f.ready().await;

    let err = f
        .ash
        .set_overrides(&id, MachineOverrides { memory_mb: Some(64), ..Default::default() })
        .expect_err("64 MB cannot run Minecraft");

    assert_eq!(err.kind(), "invalid_setting");
    // The message has to name the range, because "invalid" tells the player
    // nothing about what to type instead.
    assert!(err.user_message().contains("512"), "{}", err.user_message());
    assert_eq!(f.ash.overrides(&id).expect("unchanged").memory_mb, None);
}

#[tokio::test]
async fn only_one_heap_size_ever_reaches_the_command_line() {
    let f = fixture();
    let id = f.ready().await;
    f.ash
        .set_overrides(&id, MachineOverrides { memory_mb: Some(4096), ..Default::default() })
        .expect("set");

    let args = f.launch(&id).await;

    // Two `-Xmx` flags is not an error the JVM reports; it silently takes
    // the last one, which would make this setting look broken at random.
    assert_eq!(args.iter().filter(|a| a.starts_with("-Xmx")).count(), 1);
}

// ---- window size ---------------------------------------------------------------

#[tokio::test]
async fn window_size_reaches_the_game() {
    let f = fixture();
    let id = f.ready().await;

    f.ash
        .set_overrides(
            &id,
            MachineOverrides {
                resolution: Some(Resolution { width: 1280, height: 720 }),
                ..Default::default()
            },
        )
        .expect("set");

    let args = f.launch(&id).await;
    assert_eq!(value_of(&args, "--width"), Some("1280"));
    assert_eq!(value_of(&args, "--height"), Some("720"));
}

#[tokio::test]
async fn no_window_size_means_the_game_picks() {
    let f = fixture();
    let id = f.ready().await;

    let args = f.launch(&id).await;

    assert!(!args.iter().any(|a| a == "--width"), "ash invented a window size");
}

// ---- java ----------------------------------------------------------------------

#[tokio::test]
async fn a_chosen_java_is_used_instead_of_the_provisioned_one() {
    let f = fixture();
    let id = f.ready().await;
    let chosen = f.tmp.path().join("my-own-java.exe");
    std::fs::write(&chosen, b"not really a jvm").expect("write");

    f.ash
        .set_overrides(
            &id,
            MachineOverrides { java_executable: Some(chosen.clone()), ..Default::default() },
        )
        .expect("set");

    f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect("launch");

    // ash still never *searches* for a Java. A player naming one is a
    // different thing from ash guessing, and it is the escape hatch for a
    // machine the provisioned runtime cannot run on.
    assert_eq!(f.process.last().program, chosen);
}

#[tokio::test]
async fn a_java_path_that_does_not_exist_is_refused_when_it_is_set() {
    let f = fixture();
    let id = f.ready().await;

    let err = f
        .ash
        .set_overrides(
            &id,
            MachineOverrides {
                java_executable: Some(PathBuf::from("C:/nope/java.exe")),
                ..Default::default()
            },
        )
        .expect_err("no such file");

    // Caught here rather than at launch, where it would surface as a spawn
    // failure with nothing in it pointing at this setting.
    assert_eq!(err.kind(), "invalid_setting");
}

#[tokio::test]
async fn a_java_that_disappears_later_fails_the_launch_clearly() {
    let f = fixture();
    let id = f.ready().await;
    let chosen = f.tmp.path().join("temporary-java.exe");
    std::fs::write(&chosen, b"not really a jvm").expect("write");
    f.ash
        .set_overrides(
            &id,
            MachineOverrides { java_executable: Some(chosen.clone()), ..Default::default() },
        )
        .expect("set");
    std::fs::remove_file(&chosen).expect("remove");

    let err = f.ash.launch(&id, &NullSink, &Cancel::new()).await.expect_err("java is gone");

    assert_eq!(err.kind(), "invalid_setting");
    assert!(f.process.spawned().is_empty());
}

// ---- the boundary ---------------------------------------------------------------

#[tokio::test]
async fn no_machine_local_value_is_written_into_the_directory_a_sync_would_carry() {
    let f = fixture();
    let id = f.ready().await;

    f.ash
        .set_overrides(
            &id,
            MachineOverrides {
                memory_mb: Some(DESKTOP_MEMORY_MB),
                java_executable: None,
                resolution: Some(Resolution { width: 3840, height: 2160 }),
            },
        )
        .expect("set");

    // `instances/` is what an instance *is*: the directory a sync, a backup
    // or a copy to another machine would carry. This is the assertion the
    // whole ticket rests on, and it holds because of where the file lives -
    // not because something remembered to filter it.
    let instances = f.tmp.path().join("instances");
    let carried = files_under(&instances);
    assert!(!carried.is_empty(), "the walk found nothing, so it proved nothing");

    for (path, text) in &carried {
        for leaked in [DESKTOP_MEMORY_MB.to_string(), "3840".to_owned(), "2160".to_owned()] {
            assert!(
                !text.contains(&leaked),
                "{} carries a machine-local value: {leaked}",
                path.display()
            );
        }
    }
}

#[tokio::test]
async fn the_instance_definition_has_nowhere_to_put_a_machine_local_value() {
    let f = fixture();
    let id = f.ready().await;
    f.ash
        .set_overrides(
            &id,
            MachineOverrides { memory_mb: Some(DESKTOP_MEMORY_MB), ..Default::default() },
        )
        .expect("set");

    // The type-level half of the same boundary: whatever a future sync
    // serialises an Instance into, it cannot contain a field that is not on
    // the struct.
    let instance = f.ash.instance(&id).expect("instance");
    let encoded = serde_json::to_string(&instance).expect("encode");

    for absent in ["memory", "java", "resolution", "width", "height"] {
        assert!(!encoded.contains(absent), "Instance carries {absent}: {encoded}");
    }
}

#[tokio::test]
async fn overrides_live_under_the_data_root_not_the_instance() {
    let f = fixture();
    let id = f.ready().await;
    f.ash
        .set_overrides(&id, MachineOverrides { memory_mb: Some(4096), ..Default::default() })
        .expect("set");

    let machine = f.tmp.path().join("data/machine");
    assert!(machine.is_dir(), "machine-local settings are not under the data root");
    assert!(machine.join(format!("{id}.json")).is_file());
}

#[tokio::test]
async fn two_instances_are_tuned_independently() {
    let f = fixture();
    let big = f.ready().await;
    let small = f.ash.create_instance("small", VERSION).expect("instance").id;

    f.ash
        .set_overrides(&big, MachineOverrides { memory_mb: Some(8192), ..Default::default() })
        .expect("set");
    f.ash
        .set_overrides(&small, MachineOverrides { memory_mb: Some(1024), ..Default::default() })
        .expect("set");

    assert!(f.launch(&big).await.contains(&"-Xmx8192M".to_owned()));
    f.ash.stop_game(&big);
    assert!(f.launch(&small).await.contains(&"-Xmx1024M".to_owned()));
}

#[tokio::test]
async fn settings_survive_being_read_back() {
    let f = fixture();
    let id = f.ready().await;
    let chosen = MachineOverrides {
        memory_mb: Some(6144),
        java_executable: None,
        resolution: Some(Resolution { width: 1920, height: 1080 }),
    };

    f.ash.set_overrides(&id, chosen.clone()).expect("set");

    assert_eq!(f.ash.overrides(&id).expect("read back"), chosen);
}

#[tokio::test]
async fn deleting_an_instance_takes_its_machine_settings_with_it() {
    let f = fixture();
    let id = f.ready().await;
    f.ash
        .set_overrides(&id, MachineOverrides { memory_mb: Some(8192), ..Default::default() })
        .expect("set");

    f.ash.delete_instance(&id).expect("delete");

    // These do not live in the instance directory, so removing that
    // directory does not remove them - and an orphan would be inherited by
    // the next instance that happened to take the same id.
    let orphan = f.tmp.path().join(format!("data/machine/{id}.json"));
    assert!(!orphan.exists(), "{} outlived its instance", orphan.display());

    let replacement = f.ash.create_instance("modern", VERSION).expect("instance");
    assert_eq!(replacement.id, id, "the test only means something if the id is reused");
    assert_eq!(f.ash.overrides(&id).expect("fresh").memory_mb, None);
}

#[tokio::test]
async fn settings_for_an_instance_that_does_not_exist_are_an_error() {
    let f = fixture();
    let missing = f.ash.create_instance("temporary", VERSION).expect("instance").id;
    f.ash.delete_instance(&missing).expect("delete");

    assert_eq!(f.ash.overrides(&missing).expect_err("gone").kind(), "instance_not_found");
    assert_eq!(
        f.ash.set_overrides(&missing, MachineOverrides::default()).expect_err("gone").kind(),
        "instance_not_found"
    );
}
