//! The adapter. Every command here delegates to exactly one `ash-core`
//! operation and does nothing else - no branching on domain state, no
//! assembling of results, no business rules. If logic starts accumulating in
//! this file it belongs in `ash-core` instead, where it can be tested.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use ash_core::credentials::OsCredentialStore;
use ash_core::http::ReqwestHttp;
use ash_core::process::OsProcessPort;
use ash_core::servers::OsServerPort;
use ash_core::{
    Account, Accounts, Ash, AshAccountStatus, Cancel, Catalogue, Config, DegradationNotice,
    DeletionPreview, GameStatus, Instance, InstanceGlance, InstanceId, InvocationView,
    LauncherPreferences, Loader, MachineDefaults, MachineOverrides, News, PendingSignIn, Plan,
    PrepareEvent, ProgressSink, Runtime, ServerEntry, ServerStatus, SignInStatus,
};
use tauri::{Emitter, Manager};

/// ash's Azure application id.
///
/// A public-client OAuth client id is an identifier, not a secret - every
/// open-source launcher ships theirs in source. It is the thing Mojang's
/// allow list is keyed on.
const CLIENT_ID: &str = "d8cc6384-820e-4608-9a60-f05da43d3571";
use serde::Serialize;

/// What the UI receives when an operation fails.
///
/// `kind` is the stable discriminant to branch on; `message` is what to show
/// a player. The underlying error's `Display` never crosses this boundary.
#[derive(Debug, Serialize, Clone)]
pub struct UiError {
    kind: &'static str,
    message: String,
    /// Whether offering a retry makes sense. A banned Xbox account or an
    /// un-allow-listed client id will never succeed on a second attempt.
    retryable: bool,
}

impl From<ash_core::AshError> for UiError {
    fn from(err: ash_core::AshError) -> Self {
        Self { kind: err.kind(), message: err.user_message(), retryable: err.is_retryable() }
    }
}

#[tauri::command]
async fn catalogue(state: tauri::State<'_, AppState>) -> Result<Catalogue, UiError> {
    state.ash.catalogue().await.map_err(UiError::from)
}

#[tauri::command]
async fn refresh_catalogue(state: tauri::State<'_, AppState>) -> Result<Catalogue, UiError> {
    state.ash.refresh_catalogue().await.map_err(UiError::from)
}

// Instance commands are `async` so Tauri runs them off the main thread. The
// work itself is synchronous filesystem access; a large directory walk during
// a deletion preview should not stall the window.

/// Forwards depot progress to the window as `prepare-progress` events.
///
/// The UI derives its own display from the event stream; the adapter does not
/// compute a percentage, because a percentage cannot say "4,300 of 4,900
/// files, and the last one failed verification".
struct WindowSink(tauri::AppHandle);

impl ProgressSink for WindowSink {
    fn emit(&self, event: PrepareEvent) {
        let _ = self.0.emit("prepare-progress", event);
    }
}

/// Everything the window can reach.
///
/// One managed value rather than several: a Tauri command taking two
/// `State<'_, T>` parameters gives each an independent anonymous lifetime,
/// which the async command machinery cannot unify.
struct AppState {
    /// Behind an `Arc` so a spawned task can hold it without holding a
    /// `tauri::State` guard across an await - the guard borrows from the
    /// app handle and a spawned future has to be `'static`.
    ash: Arc<Ash>,
    /// Cancel handles for preparation in flight, by instance.
    ///
    /// Keyed rather than a single slot: two instances can be launched at
    /// once, and one shared handle would mean cancelling either one reached
    /// whichever started most recently.
    preparing: Mutex<HashMap<String, Cancel>>,
}

// ---- sign-in ----

#[tauri::command]
async fn begin_sign_in(state: tauri::State<'_, AppState>) -> Result<PendingSignIn, UiError> {
    state.ash.begin_sign_in().await.map_err(UiError::from)
}

#[tauri::command]
async fn poll_sign_in(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
) -> Result<SignInStatus, UiError> {
    let status = state.ash.poll_sign_in().await.map_err(UiError::from)?;
    if let SignInStatus::Complete { account } = &status {
        spawn_ash_sign_in(app, Arc::clone(&state.ash), account.profile_id.clone());
    }
    Ok(status)
}

// ---- the ash account (spec 0004) ----

#[derive(Clone, Serialize)]
struct AshAccountEvent {
    profile_id: String,
    status: AshAccountStatus,
}

/// Sign a player in to their ash account in the background, and tell the
/// window how it went. Never awaited by anything on the way to playing: if
/// ash's servers are slow or down, nothing the player is doing waits.
fn spawn_ash_sign_in(app: tauri::AppHandle, ash: Arc<Ash>, profile_id: String) {
    tauri::async_runtime::spawn(async move {
        let status = ash.sign_in_to_ash(&profile_id).await;
        let _ = app.emit("ash-account", AshAccountEvent { profile_id, status });
    });
}

#[tauri::command]
fn ash_account_status(state: tauri::State<'_, AppState>, profile_id: String) -> AshAccountStatus {
    state.ash.ash_account_status(&profile_id)
}

#[tauri::command]
async fn delete_ash_account(
    state: tauri::State<'_, AppState>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    state.ash.delete_ash_account(&profile_id).await.map_err(UiError::from)
}

#[tauri::command]
async fn dismiss_ash_account_notice(
    state: tauri::State<'_, AppState>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    state.ash.dismiss_ash_account_notice(&profile_id).map_err(UiError::from)
}

// ---- news (spec 0004) ----

#[tauri::command]
async fn news(state: tauri::State<'_, AppState>) -> Result<News, UiError> {
    Ok(state.ash.news().await)
}

#[tauri::command]
async fn mark_news_seen(state: tauri::State<'_, AppState>) -> Result<(), UiError> {
    state.ash.mark_news_seen().map_err(UiError::from)
}

/// A link in a news post. Only https, checked here as well as by the page's
/// parser: whatever the window asks, nothing but a web page opens.
#[tauri::command]
fn open_news_link(url: String) -> Result<(), UiError> {
    let refused = || UiError {
        kind: "open_failed",
        message: "That link can't be opened.".into(),
        retryable: false,
    };
    if !url.starts_with("https://") || url.chars().any(char::is_whitespace) {
        return Err(refused());
    }
    tauri_plugin_opener::open_url(&url, None::<&str>).map_err(|_| refused())
}

/// The one web page the launcher opens: what an ash account holds. A fixed
/// address rather than one the window names, so no page can open any other.
#[tauri::command]
fn open_privacy_statement() -> Result<(), UiError> {
    tauri_plugin_opener::open_url("https://ashlauncher.com/privacy.html", None::<&str>).map_err(
        |_| UiError {
            kind: "open_failed",
            message: "Could not open the privacy statement. It's at ashlauncher.com/privacy.html."
                .into(),
            retryable: true,
        },
    )
}

#[tauri::command]
async fn cancel_sign_in(state: tauri::State<'_, AppState>) -> Result<(), UiError> {
    state.ash.cancel_sign_in();
    Ok(())
}

#[tauri::command]
async fn accounts(state: tauri::State<'_, AppState>) -> Result<Accounts, UiError> {
    Ok(state.ash.accounts())
}

#[tauri::command]
async fn select_account(
    state: tauri::State<'_, AppState>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    state.ash.select_account(&profile_id).map_err(UiError::from)
}

#[tauri::command]
async fn remove_account(
    state: tauri::State<'_, AppState>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    state.ash.remove_account(&profile_id).map_err(UiError::from)
}

#[tauri::command]
async fn ensure_session(
    state: tauri::State<'_, AppState>,
    profile_id: String,
) -> Result<Account, UiError> {
    state.ash.ensure_session(&profile_id).await.map_err(UiError::from)
}

// ---- instances ----

#[tauri::command]
async fn create_instance(
    state: tauri::State<'_, AppState>,
    name: String,
    version_id: String,
    loader: Loader,
) -> Result<Instance, UiError> {
    state.ash.create_instance(&name, &version_id, loader).map_err(UiError::from)
}

#[tauri::command]
async fn loaders_for(
    state: tauri::State<'_, AppState>,
    version_id: String,
) -> Result<Vec<Loader>, UiError> {
    Ok(state.ash.loaders_for(&version_id))
}

#[tauri::command]
async fn instances(state: tauri::State<'_, AppState>) -> Result<Vec<Instance>, UiError> {
    state.ash.instances().map_err(UiError::from)
}

#[tauri::command]
async fn rename_instance(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    name: String,
) -> Result<Instance, UiError> {
    state.ash.rename_instance(&id, &name).map_err(UiError::from)
}

#[tauri::command]
async fn preview_deletion(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<DeletionPreview, UiError> {
    state.ash.preview_deletion(&id).map_err(UiError::from)
}

#[tauri::command]
async fn delete_instance(state: tauri::State<'_, AppState>, id: InstanceId) -> Result<(), UiError> {
    state.ash.delete_instance(&id).map_err(UiError::from)
}

// ---- preparation ----

#[tauri::command]
async fn plan_instance(state: tauri::State<'_, AppState>, id: InstanceId) -> Result<Plan, UiError> {
    state.ash.plan_instance(&id).await.map_err(UiError::from)
}

/// Start preparing an instance and return at once.
///
/// Preparation can take minutes over thousands of files, which is far too
/// long to hold an IPC call open. Progress arrives as `prepare-progress`
/// events and the outcome as `prepare-finished`, so the window stays
/// responsive and a cancel actually reaches the running work.
#[tauri::command]
fn prepare_instance(app: tauri::AppHandle, id: InstanceId) -> Result<(), UiError> {
    tauri::async_runtime::spawn(async move {
        let cancel = Cancel::new();

        // Take what the download needs and let the state guard go before any
        // await: the guard borrows from the app handle, and this future has
        // to outlive this function.
        let ash = {
            let state = app.state::<AppState>();
            state.preparing.lock().unwrap().insert(id.as_str().to_owned(), cancel.clone());
            Arc::clone(&state.ash)
        };

        let sink = WindowSink(app.clone());
        let outcome = ash.prepare_instance(&id, &sink, &cancel).await;

        {
            let state = app.state::<AppState>();
            state.preparing.lock().unwrap().remove(id.as_str());
        }

        let _ = app.emit(
            "prepare-finished",
            match outcome {
                Ok(plan) => PrepareOutcome { ok: true, plan: Some(plan), error: None },
                Err(e) => PrepareOutcome { ok: false, plan: None, error: Some(UiError::from(e)) },
            },
        );
    });
    Ok(())
}

#[derive(Debug, Serialize, Clone)]
struct PrepareOutcome {
    ok: bool,
    plan: Option<Plan>,
    error: Option<UiError>,
}

// ---- machine-local settings ----
//
// Kept distinct from the instance commands on purpose: these are the values
// Phase 4 sync must not carry, and a command named `update_instance` that
// happened to take a memory figure is exactly how that boundary erodes.

/// What ash passes when the player has not chosen.
///
/// Served rather than duplicated in the UI: a hardcoded copy that drifts
/// would show a player one figure while the JVM got another.
/// What an instance with no memory of its own is given on this machine.
#[tauri::command]
async fn default_memory_mb(state: tauri::State<'_, AppState>) -> Result<u32, UiError> {
    Ok(state.ash.default_memory_mb())
}

/// ash's own default, which a machine with no default of its own follows.
#[tauri::command]
async fn ash_default_memory_mb() -> Result<u32, UiError> {
    Ok(ash_core::DEFAULT_MEMORY_MB)
}

#[tauri::command]
async fn machine_defaults(state: tauri::State<'_, AppState>) -> Result<MachineDefaults, UiError> {
    Ok(state.ash.machine_defaults())
}

#[tauri::command]
async fn set_machine_defaults(
    state: tauri::State<'_, AppState>,
    defaults: MachineDefaults,
) -> Result<MachineDefaults, UiError> {
    state.ash.set_machine_defaults(defaults).map_err(UiError::from)
}

#[tauri::command]
async fn overrides(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<MachineOverrides, UiError> {
    state.ash.overrides(&id).map_err(UiError::from)
}

#[tauri::command]
async fn degradation_notice(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Option<DegradationNotice>, UiError> {
    state.ash.degradation_notice(&id).map_err(UiError::from)
}

#[tauri::command]
async fn instance_glance(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<InstanceGlance, UiError> {
    state.ash.instance_glance(&id).map_err(UiError::from)
}

#[tauri::command]
async fn set_overrides(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    settings: MachineOverrides,
) -> Result<MachineOverrides, UiError> {
    state.ash.set_overrides(&id, settings).map_err(UiError::from)
}

#[tauri::command]
fn launcher_preferences(state: tauri::State<'_, AppState>) -> LauncherPreferences {
    state.ash.launcher_preferences()
}

#[tauri::command]
fn set_launcher_preferences(
    state: tauri::State<'_, AppState>,
    preferences: LauncherPreferences,
) -> Result<LauncherPreferences, UiError> {
    state.ash.set_launcher_preferences(preferences).map_err(UiError::from)
}

// ---- launching ----

/// Start the game.
///
/// Spawned rather than awaited for the same reason preparation is: launching
/// prepares whatever is missing first, which can take minutes. Progress
/// arrives as `prepare-progress` and the outcome as `launch-finished`.
#[tauri::command]
fn launch(app: tauri::AppHandle, id: InstanceId) -> Result<(), UiError> {
    start(app, id, Play::Launch);
    Ok(())
}

/// Start the game straight into one of the instance's servers, reported on
/// the same events as `launch`.
#[tauri::command]
fn join(app: tauri::AppHandle, id: InstanceId, address: String) -> Result<(), UiError> {
    start(app, id, Play::Join(address));
    Ok(())
}

/// Start the game with the player's own mods left out, this once: what a
/// crash with them on offers. Reported on the same events as `launch`.
#[tauri::command]
fn launch_without_third_party_mods(app: tauri::AppHandle, id: InstanceId) -> Result<(), UiError> {
    start(app, id, Play::WithoutThirdPartyMods);
    Ok(())
}

/// The three ways the window starts a game.
enum Play {
    Launch,
    Join(String),
    WithoutThirdPartyMods,
}

fn start(app: tauri::AppHandle, id: InstanceId, play: Play) {
    tauri::async_runtime::spawn(async move {
        let cancel = Cancel::new();
        let ash = {
            let state = app.state::<AppState>();
            state.preparing.lock().unwrap().insert(id.as_str().to_owned(), cancel.clone());
            Arc::clone(&state.ash)
        };

        let sink = WindowSink(app.clone());
        let outcome = match &play {
            Play::Join(address) => ash.join(&id, address, &sink, &cancel).await,
            Play::Launch => ash.launch(&id, &sink, &cancel).await,
            Play::WithoutThirdPartyMods => {
                ash.launch_without_third_party_mods(&id, &sink, &cancel).await
            }
        };

        {
            let state = app.state::<AppState>();
            state.preparing.lock().unwrap().remove(id.as_str());
        }

        let _ = app.emit(
            "launch-finished",
            match outcome {
                Ok(invocation) => {
                    LaunchOutcome { ok: true, invocation: Some(invocation), error: None }
                }
                Err(e) => {
                    LaunchOutcome { ok: false, invocation: None, error: Some(UiError::from(e)) }
                }
            },
        );
    });
}

// ---- servers ----

#[tauri::command]
async fn servers(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Vec<ServerEntry>, UiError> {
    state.ash.servers(&id).map_err(UiError::from)
}

#[tauri::command]
async fn server_list(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Vec<ServerEntry>, UiError> {
    state.ash.server_list(&id).map_err(UiError::from)
}

#[tauri::command]
async fn add_server(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    name: String,
    address: String,
) -> Result<(), UiError> {
    state.ash.add_server(&id, &name, &address).map_err(UiError::from)
}

#[tauri::command]
async fn edit_server(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    position: usize,
    expected: String,
    name: String,
    address: String,
) -> Result<(), UiError> {
    state.ash.edit_server(&id, position, &expected, &name, &address).map_err(UiError::from)
}

#[tauri::command]
async fn remove_server(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    position: usize,
    expected: String,
) -> Result<(), UiError> {
    state.ash.remove_server(&id, position, &expected).map_err(UiError::from)
}

#[tauri::command]
async fn move_server(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    position: usize,
    expected: String,
    to: usize,
) -> Result<(), UiError> {
    state.ash.move_server(&id, position, &expected, to).map_err(UiError::from)
}

#[tauri::command]
async fn server_status(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
    address: String,
) -> Result<ServerStatus, UiError> {
    let ash = Arc::clone(&state.ash);
    ash.server_status(&id, &address).await.map_err(UiError::from)
}

#[derive(Debug, Serialize, Clone)]
struct LaunchOutcome {
    ok: bool,
    invocation: Option<InvocationView>,
    error: Option<UiError>,
}

/// Exactly what ash would run, without running it.
#[tauri::command]
async fn preview_launch(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<InvocationView, UiError> {
    let ash = Arc::clone(&state.ash);
    let sink = WindowSink(app);
    ash.preview_launch(&id, &sink, &Cancel::new()).await.map_err(UiError::from)
}

/// Polled by the window while a game is up. `None` means ash never started
/// this instance.
#[tauri::command]
async fn game_status(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Option<GameStatus>, UiError> {
    Ok(state.ash.game_status(&id))
}

#[tauri::command]
async fn game_log(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Vec<String>, UiError> {
    Ok(state.ash.game_log(&id))
}

#[tauri::command]
async fn stop_game(state: tauri::State<'_, AppState>, id: InstanceId) -> Result<(), UiError> {
    state.ash.stop_game(&id);
    Ok(())
}

/// Which Java runtime an instance will use, provisioning it if needed.
///
/// Deliberately not "which Java is installed": ash never consults the
/// machine's Java, so the only answer is one ash downloaded itself.
#[tauri::command]
async fn ensure_runtime(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<Runtime, UiError> {
    let ash = Arc::clone(&state.ash);
    let sink = WindowSink(app);
    ash.ensure_runtime(&id, &sink, &Cancel::new()).await.map_err(UiError::from)
}

#[tauri::command]
async fn cancel_preparation(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<(), UiError> {
    if let Some(cancel) = state.preparing.lock().unwrap().get(id.as_str()) {
        cancel.cancel();
    }
    Ok(())
}

/// Open the folder holding ash's own log.
///
/// The log is the first thing anyone will ask for when a launch goes wrong,
/// so getting to it should not involve knowing where LOCALAPPDATA is.
#[tauri::command]
async fn reveal_log(state: tauri::State<'_, AppState>) -> Result<(), UiError> {
    let path = state.ash.diagnostics().path().to_path_buf();
    // Revealing a file that does not exist yet fails; the folder always
    // does once ash has written anything at all.
    let target = if path.is_file() {
        path
    } else {
        path.parent().map(std::path::Path::to_path_buf).unwrap_or(path)
    };
    tauri_plugin_opener::reveal_item_in_dir(&target).map_err(|_| UiError {
        kind: "reveal_failed",
        message: "Could not open the log folder.".into(),
        retryable: true,
    })
}

/// The instance's `mods` folder, opened itself rather than shown in its
/// parent: it is where the player is about to drop a jar.
#[tauri::command]
async fn reveal_mods_folder(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<(), UiError> {
    let path = state.ash.mods_directory(&id).map_err(UiError::from)?;
    tauri_plugin_opener::open_path(&path, None::<&str>).map_err(|_| UiError {
        kind: "reveal_failed",
        // No path in the message: user-facing text never carries one.
        message: "Could not open the mods folder.".into(),
        retryable: true,
    })
}

/// Opening a file manager is an OS concern, so it lives here rather than in
/// ash-core, which only says *which* directory.
#[tauri::command]
async fn reveal_game_directory(
    state: tauri::State<'_, AppState>,
    id: InstanceId,
) -> Result<(), UiError> {
    let path = state.ash.game_directory(&id);
    tauri_plugin_opener::reveal_item_in_dir(&path).map_err(|_| UiError {
        kind: "reveal_failed",
        // No path in the message: user-facing text never carries one.
        message: "Could not open the instance folder.".into(),
        retryable: true,
    })
}

fn ash_state(client_root: std::path::PathBuf) -> Ash {
    let base = dirs_next_data_dir().join("ash");
    Ash::new(
        Config { client_root, ..Config::rooted_at(base) },
        Arc::new(ReqwestHttp::new()),
        Arc::new(OsCredentialStore::new()),
        Arc::new(OsProcessPort::new()),
        Arc::new(OsServerPort::new()),
        CLIENT_ID,
    )
}

/// Where the installer put ash's own client jars.
///
/// Part of the installation rather than of ash's storage, which is why it is
/// the one root `Config::rooted_at` cannot work out: the jars ship inside the
/// installer so that the launcher and the client can never be version-skewed.
/// Resolving it is the adapter's job, like every other path.
fn client_dir(app: &tauri::AppHandle) -> std::path::PathBuf {
    #[cfg(debug_assertions)]
    if let Some(dir) = dev_client_dir() {
        return dir;
    }
    app.path()
        .resource_dir()
        // Resolving the resource directory only fails on a broken
        // installation. The executable's own directory is where the installer
        // puts resources on Windows anyway, so this is the same answer by
        // another route - and unlike a path relative to the working directory
        // it cannot be steered by wherever ash happened to be started from,
        // which would let a jar dropped there be loaded into the game.
        .or_else(|_| std::env::current_exe().map(|exe| exe.with_file_name("")))
        .map(|dir| dir.join("client"))
        // Both failing at once is a machine ash cannot run on. It surfaces as
        // ash-core's typed "client missing" the first time a modded instance
        // is prepared, which is a better moment to tell the player than a
        // panic before the window opens.
        .unwrap_or_default()
}

/// The client jars `./gradlew build` made, for `npm run tauri dev`.
///
/// A dev build has no installation: only `build:installer` copies the jars
/// into `client/` beside the executable, so without this every ash-client
/// instance in dev stopped at "ash's own client is missing". Each target's
/// jar is copied into one directory, the shape an installation has, as the
/// real-launch example does.
///
/// Debug builds only, and found from where this crate was compiled, never
/// from the working directory. An installed ash never looks here.
#[cfg(debug_assertions)]
fn dev_client_dir() -> Option<std::path::PathBuf> {
    let client = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../client");
    let staged = client.join("build").join("dev-run");
    std::fs::create_dir_all(&staged).ok()?;

    let mut staged_any = false;
    for module in std::fs::read_dir(&client).ok()?.flatten() {
        let Ok(jars) = std::fs::read_dir(module.path().join("build").join("libs")) else {
            continue;
        };
        for jar in jars.flatten() {
            let name = jar.file_name().to_string_lossy().into_owned();
            if name.starts_with("ash-client-") && name.ends_with(".jar") {
                staged_any |= std::fs::copy(jar.path(), staged.join(&name)).is_ok();
            }
        }
    }

    if !staged_any {
        eprintln!(
            "ash: no ash-client-*.jar under client/*/build/libs; run ./gradlew build in client/"
        );
        return None;
    }
    Some(staged)
}

/// Resolving this is the adapter's job, not the library's - `ash-core` never
/// reads the environment, which is what lets tests point it at a temp dir.
fn dirs_next_data_dir() -> std::path::PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(std::path::PathBuf::from)
        .or_else(|| {
            std::env::var_os("HOME").map(|h| std::path::PathBuf::from(h).join(".local/share"))
        })
        .unwrap_or_else(std::env::temp_dir)
}

/// `ash --self-check <file>`: where to write the installation check's report,
/// if ash was started for one rather than for a player.
fn self_check_report() -> Option<std::path::PathBuf> {
    let mut args = std::env::args_os().skip(1);
    while let Some(arg) = args.next() {
        if arg == "--self-check" {
            return args.next().map(std::path::PathBuf::from);
        }
    }
    None
}

/// Checks the installation from where the installed launcher looks - the
/// client folder as Tauri resolves it, which is where a launch on 8 October
/// 2026 went wrong - writes the report, and exits: 0 if all is well, 1 if not,
/// 2 if the report could not be written. ash does nothing else in this mode.
/// CI installs the real installer and runs it; it also serves to diagnose a
/// player's installation.
fn self_check(ash: &Ash, report: &std::path::Path) -> ! {
    let check = ash.check_installation();
    let code = match std::fs::write(report, check.report()) {
        Ok(()) if check.ok => 0,
        Ok(()) => 1,
        Err(_) => 2,
    };
    std::process::exit(code)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        // In `setup` rather than on the builder, because the client jars live
        // in the installation and only an `AppHandle` knows where that is.
        .setup(|app| {
            let ash = Arc::new(ash_state(client_dir(app.handle())));
            if let Some(report) = self_check_report() {
                self_check(&ash, &report);
            }
            // Renew ash sessions that are missing or ending soon, in the
            // background, for every account on this machine.
            for account in ash.accounts().accounts {
                if ash.needs_ash_sign_in(&account.profile_id) {
                    spawn_ash_sign_in(app.handle().clone(), Arc::clone(&ash), account.profile_id);
                }
            }
            app.manage(AppState { ash, preparing: Mutex::new(HashMap::new()) });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            catalogue,
            refresh_catalogue,
            begin_sign_in,
            poll_sign_in,
            cancel_sign_in,
            accounts,
            select_account,
            remove_account,
            ensure_session,
            ash_account_status,
            delete_ash_account,
            dismiss_ash_account_notice,
            open_privacy_statement,
            news,
            mark_news_seen,
            open_news_link,
            create_instance,
            loaders_for,
            instances,
            rename_instance,
            preview_deletion,
            delete_instance,
            reveal_game_directory,
            reveal_mods_folder,
            launch_without_third_party_mods,
            plan_instance,
            prepare_instance,
            ensure_runtime,
            cancel_preparation,
            launch,
            join,
            servers,
            server_list,
            add_server,
            edit_server,
            remove_server,
            move_server,
            server_status,
            preview_launch,
            game_status,
            game_log,
            stop_game,
            overrides,
            set_overrides,
            launcher_preferences,
            set_launcher_preferences,
            degradation_notice,
            instance_glance,
            default_memory_mb,
            ash_default_memory_mb,
            machine_defaults,
            set_machine_defaults,
            reveal_log
        ])
        .run(tauri::generate_context!())
        .expect("error while running ash");
}
