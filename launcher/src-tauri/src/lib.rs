//! The adapter. Every command here delegates to exactly one `ash-core`
//! operation and does nothing else - no branching on domain state, no
//! assembling of results, no business rules. If logic starts accumulating in
//! this file it belongs in `ash-core` instead, where it can be tested.

use std::sync::Arc;

use ash_core::credentials::OsCredentialStore;
use ash_core::http::ReqwestHttp;
use ash_core::{
    Account, Accounts, Ash, Catalogue, Config, DeletionPreview, Instance, InstanceId,
    PendingSignIn, SignInStatus,
};

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
#[derive(Debug, Serialize)]
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
async fn catalogue(ash: tauri::State<'_, Ash>) -> Result<Catalogue, UiError> {
    ash.catalogue().await.map_err(UiError::from)
}

#[tauri::command]
async fn refresh_catalogue(ash: tauri::State<'_, Ash>) -> Result<Catalogue, UiError> {
    ash.refresh_catalogue().await.map_err(UiError::from)
}

// Instance commands are `async` so Tauri runs them off the main thread. The
// work itself is synchronous filesystem access; a large directory walk during
// a deletion preview should not stall the window.

// ---- sign-in ----

#[tauri::command]
async fn begin_sign_in(ash: tauri::State<'_, Ash>) -> Result<PendingSignIn, UiError> {
    ash.begin_sign_in().await.map_err(UiError::from)
}

#[tauri::command]
async fn poll_sign_in(ash: tauri::State<'_, Ash>) -> Result<SignInStatus, UiError> {
    ash.poll_sign_in().await.map_err(UiError::from)
}

#[tauri::command]
async fn cancel_sign_in(ash: tauri::State<'_, Ash>) -> Result<(), UiError> {
    ash.cancel_sign_in();
    Ok(())
}

#[tauri::command]
async fn accounts(ash: tauri::State<'_, Ash>) -> Result<Accounts, UiError> {
    Ok(ash.accounts())
}

#[tauri::command]
async fn select_account(
    ash: tauri::State<'_, Ash>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    ash.select_account(&profile_id).map_err(UiError::from)
}

#[tauri::command]
async fn remove_account(
    ash: tauri::State<'_, Ash>,
    profile_id: String,
) -> Result<Accounts, UiError> {
    ash.remove_account(&profile_id).map_err(UiError::from)
}

#[tauri::command]
async fn ensure_session(
    ash: tauri::State<'_, Ash>,
    profile_id: String,
) -> Result<Account, UiError> {
    ash.ensure_session(&profile_id).await.map_err(UiError::from)
}

// ---- instances ----

#[tauri::command]
async fn create_instance(
    ash: tauri::State<'_, Ash>,
    name: String,
    version_id: String,
) -> Result<Instance, UiError> {
    ash.create_instance(&name, &version_id).map_err(UiError::from)
}

#[tauri::command]
async fn instances(ash: tauri::State<'_, Ash>) -> Result<Vec<Instance>, UiError> {
    ash.instances().map_err(UiError::from)
}

#[tauri::command]
async fn rename_instance(
    ash: tauri::State<'_, Ash>,
    id: InstanceId,
    name: String,
) -> Result<Instance, UiError> {
    ash.rename_instance(&id, &name).map_err(UiError::from)
}

#[tauri::command]
async fn preview_deletion(
    ash: tauri::State<'_, Ash>,
    id: InstanceId,
) -> Result<DeletionPreview, UiError> {
    ash.preview_deletion(&id).map_err(UiError::from)
}

#[tauri::command]
async fn delete_instance(ash: tauri::State<'_, Ash>, id: InstanceId) -> Result<(), UiError> {
    ash.delete_instance(&id).map_err(UiError::from)
}

/// Opening a file manager is an OS concern, so it lives here rather than in
/// ash-core, which only says *which* directory.
#[tauri::command]
async fn reveal_game_directory(ash: tauri::State<'_, Ash>, id: InstanceId) -> Result<(), UiError> {
    let path = ash.game_directory(&id);
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
        .manage(ash_state())
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
            reveal_game_directory
        ])
        .run(tauri::generate_context!())
        .expect("error while running ash");
}
