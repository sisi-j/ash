//! The adapter. Every command here delegates to exactly one `ash-core`
//! operation and does nothing else - no branching on domain state, no
//! assembling of results, no business rules. If logic starts accumulating in
//! this file it belongs in `ash-core` instead, where it can be tested.

use std::sync::{Arc, Mutex};

use ash_core::credentials::OsCredentialStore;
use ash_core::http::ReqwestHttp;
use ash_core::{
    Account, Accounts, Ash, Cancel, Catalogue, Config, DeletionPreview, Instance, InstanceId,
    PendingSignIn, Plan, PrepareEvent, ProgressSink, SignInStatus,
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
    /// The cancel handle for whatever preparation is running. One at a time:
    /// a player preparing two instances at once would only be competing with
    /// themselves for bandwidth.
    preparing: Mutex<Option<Cancel>>,
}

// ---- sign-in ----

#[tauri::command]
async fn begin_sign_in(state: tauri::State<'_, AppState>) -> Result<PendingSignIn, UiError> {
    state.ash.begin_sign_in().await.map_err(UiError::from)
}

#[tauri::command]
async fn poll_sign_in(state: tauri::State<'_, AppState>) -> Result<SignInStatus, UiError> {
    state.ash.poll_sign_in().await.map_err(UiError::from)
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
) -> Result<Instance, UiError> {
    state.ash.create_instance(&name, &version_id).map_err(UiError::from)
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
            *state.preparing.lock().unwrap() = Some(cancel.clone());
            Arc::clone(&state.ash)
        };

        let sink = WindowSink(app.clone());
        let outcome = ash.prepare_instance(&id, &sink, &cancel).await;

        {
            let state = app.state::<AppState>();
            *state.preparing.lock().unwrap() = None;
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

#[tauri::command]
async fn cancel_preparation(state: tauri::State<'_, AppState>) -> Result<(), UiError> {
    if let Some(cancel) = state.preparing.lock().unwrap().as_ref() {
        cancel.cancel();
    }
    Ok(())
}

/// Opening a file manager is an OS concern, so it lives here rather than in
/// ash-core, which only says *which* directory.
#[tauri::command]
async fn reveal_game_directory(state: tauri::State<'_, AppState>, id: InstanceId) -> Result<(), UiError> {
    let path = state.ash.game_directory(&id);
    tauri_plugin_opener::reveal_item_in_dir(&path).map_err(|_| UiError {
        kind: "reveal_failed",
        // No path in the message: user-facing text never carries one.
        message: "Could not open the instance folder.".into(),
        retryable: true,
    })
}

fn ash_state() -> Ash {
    let base = dirs_next_data_dir().join("ash");
    Ash::new(
        Config::rooted_at(base),
        Arc::new(ReqwestHttp::new()),
        Arc::new(OsCredentialStore::new()),
        CLIENT_ID,
    )
}

/// Resolving this is the adapter's job, not the library's - `ash-core` never
/// reads the environment, which is what lets tests point it at a temp dir.
fn dirs_next_data_dir() -> std::path::PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(std::path::PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| std::path::PathBuf::from(h).join(".local/share")))
        .unwrap_or_else(std::env::temp_dir)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(AppState { ash: Arc::new(ash_state()), preparing: Mutex::new(None) })
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
            create_instance,
            instances,
            rename_instance,
            preview_deletion,
            delete_instance,
            reveal_game_directory,
            plan_instance,
            prepare_instance,
            cancel_preparation
        ])
        .run(tauri::generate_context!())
        .expect("error while running ash");
}
