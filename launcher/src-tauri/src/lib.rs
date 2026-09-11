//! The adapter. Every command here delegates to exactly one `ash-core`
//! operation and does nothing else - no branching on domain state, no
//! assembling of results, no business rules. If logic starts accumulating in
//! this file it belongs in `ash-core` instead, where it can be tested.

use std::sync::Arc;

use ash_core::http::ReqwestHttp;
use ash_core::{Ash, Catalogue, Config, DeletionPreview, Instance, InstanceId};
use serde::Serialize;

/// What the UI receives when an operation fails.
///
/// `kind` is the stable discriminant to branch on; `message` is what to show
/// a player. The underlying error's `Display` never crosses this boundary.
#[derive(Debug, Serialize)]
pub struct UiError {
    kind: &'static str,
    message: String,
}

impl From<ash_core::AshError> for UiError {
    fn from(err: ash_core::AshError) -> Self {
        Self { kind: err.kind(), message: err.user_message() }
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
    })
}

fn ash_state() -> Ash {
    let base = dirs_next_data_dir().join("ash");
    Ash::new(Config::rooted_at(base), Arc::new(ReqwestHttp::new()))
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
