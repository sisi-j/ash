//! The adapter. Every command here delegates to exactly one `ash-core`
//! operation and does nothing else - no branching on domain state, no
//! assembling of results, no business rules. If logic starts accumulating in
//! this file it belongs in `ash-core` instead, where it can be tested.

use std::sync::Arc;

use ash_core::http::ReqwestHttp;
use ash_core::{Ash, Catalogue, Config};
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
        .manage(ash_state())
        .invoke_handler(tauri::generate_handler![catalogue, refresh_catalogue])
        .run(tauri::generate_context!())
        .expect("error while running ash");
}
