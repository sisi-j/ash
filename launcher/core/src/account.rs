use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::credentials::CredentialStore;
use crate::error::AshError;

const ACCOUNTS_FILE: &str = "accounts.json";

/// A player ash knows about.
///
/// Keyed by Minecraft profile UUID per ADR-0009, never by a Microsoft account
/// identifier. Nothing secret lives in this struct - it is written to a plain
/// file, and the refresh token goes to the OS credential store instead.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Account {
    /// Undashed Minecraft profile UUID.
    pub profile_id: String,
    pub username: String,
    pub skin_url: Option<String>,
    pub added_at_ms: u64,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Accounts {
    pub accounts: Vec<Account>,
    /// Profile id of the account launches use. `None` only when there are no
    /// accounts at all.
    pub active: Option<String>,
}

impl Accounts {
    pub fn active_account(&self) -> Option<&Account> {
        let id = self.active.as_deref()?;
        self.accounts.iter().find(|a| a.profile_id == id)
    }
}

fn accounts_path(data_root: &Path) -> PathBuf {
    data_root.join(ACCOUNTS_FILE)
}

/// The credential-store key holding one account's refresh token.
fn refresh_key(profile_id: &str) -> String {
    format!("refresh-token:{profile_id}")
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or_default()
}

pub(crate) fn load(data_root: &Path) -> Accounts {
    fs::read(accounts_path(data_root))
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok())
        .unwrap_or_default()
}

fn save(data_root: &Path, accounts: &Accounts) -> Result<(), AshError> {
    fs::create_dir_all(data_root)
        .map_err(|e| AshError::Storage { detail: format!("creating the data directory: {e}") })?;
    let encoded = serde_json::to_vec_pretty(accounts)
        .map_err(|e| AshError::Storage { detail: format!("encoding accounts: {e}") })?;
    fs::write(accounts_path(data_root), encoded)
        .map_err(|e| AshError::Storage { detail: format!("writing accounts: {e}") })
}

/// Record a completed sign-in.
///
/// Signing into an account that already exists updates it in place rather than
/// duplicating it - the profile UUID is the key, so the same player signing in
/// twice is one account, not two.
pub(crate) fn upsert(
    data_root: &Path,
    credentials: &dyn CredentialStore,
    profile_id: &str,
    username: &str,
    skin_url: Option<String>,
    refresh_token: Option<&str>,
) -> Result<Account, AshError> {
    if let Some(token) = refresh_token {
        credentials.set(&refresh_key(profile_id), token)?;
    }

    let mut state = load(data_root);
    let account = match state.accounts.iter_mut().find(|a| a.profile_id == profile_id) {
        Some(existing) => {
            existing.username = username.to_owned();
            existing.skin_url = skin_url;
            existing.clone()
        }
        None => {
            let account = Account {
                profile_id: profile_id.to_owned(),
                username: username.to_owned(),
                skin_url,
                added_at_ms: now_ms(),
            };
            state.accounts.push(account.clone());
            account
        }
    };

    // A fresh sign-in becomes the active account; that is what the player
    // just expressed an intent about.
    state.active = Some(profile_id.to_owned());
    save(data_root, &state)?;
    Ok(account)
}

pub(crate) fn select(data_root: &Path, profile_id: &str) -> Result<Accounts, AshError> {
    let mut state = load(data_root);
    if !state.accounts.iter().any(|a| a.profile_id == profile_id) {
        return Err(AshError::SessionExpired);
    }
    state.active = Some(profile_id.to_owned());
    save(data_root, &state)?;
    Ok(state)
}

/// Forget an account and erase its stored token.
///
/// Both halves matter: dropping the account from the list while leaving a
/// refresh token in the credential store would leave a usable credential
/// behind on a machine the player thinks they signed out of.
pub(crate) fn remove(
    data_root: &Path,
    credentials: &dyn CredentialStore,
    profile_id: &str,
) -> Result<Accounts, AshError> {
    credentials.delete(&refresh_key(profile_id))?;

    let mut state = load(data_root);
    state.accounts.retain(|a| a.profile_id != profile_id);
    if state.active.as_deref() == Some(profile_id) {
        state.active = state.accounts.first().map(|a| a.profile_id.clone());
    }
    save(data_root, &state)?;
    Ok(state)
}

pub(crate) fn refresh_token(
    credentials: &dyn CredentialStore,
    profile_id: &str,
) -> Result<String, AshError> {
    credentials.get(&refresh_key(profile_id))?.ok_or(AshError::SessionExpired)
}
