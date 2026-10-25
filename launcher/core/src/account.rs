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
    /// ash's backend made an ash account for this player, and the launcher
    /// has not yet told them so. Cleared when the notice is dismissed.
    #[serde(default)]
    pub ash_account_notice: bool,
    /// The player deleted their ash account on this machine. ash does not
    /// make a new one in the background; signing in to Microsoft again does.
    #[serde(default)]
    pub ash_account_deleted: bool,
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
    let mut accounts: Accounts = fs::read(accounts_path(data_root))
        .ok()
        .and_then(|raw| serde_json::from_slice(&raw).ok())
        .unwrap_or_default();
    // Skins recorded before ash asked for them over https.
    for account in &mut accounts.accounts {
        account.skin_url = account.skin_url.take().map(secure_skin_url);
    }
    accounts
}

/// Mojang names skins with `http://textures.minecraft.net/...`, and the
/// launcher's window loads images over https only, so a face drawn from that
/// address never appears. The same server answers over https.
pub(crate) fn secure_skin_url(url: String) -> String {
    match url.strip_prefix("http://textures.minecraft.net/") {
        Some(rest) => format!("https://textures.minecraft.net/{rest}"),
        None => url,
    }
}

fn save(data_root: &Path, accounts: &Accounts) -> Result<(), AshError> {
    fs::create_dir_all(data_root).map_err(AshError::writing("creating the data directory"))?;
    let encoded = serde_json::to_vec_pretty(accounts)
        .map_err(|e| AshError::Storage { detail: format!("encoding accounts: {e}") })?;
    fs::write(accounts_path(data_root), encoded).map_err(AshError::writing("writing accounts"))
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
                ash_account_notice: false,
                ash_account_deleted: false,
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

/// Record that the player deleted their ash account on this machine, or
/// that a fresh Microsoft sign-in undid that.
pub(crate) fn set_ash_account_deleted(
    data_root: &Path,
    profile_id: &str,
    deleted: bool,
) -> Result<Accounts, AshError> {
    let mut state = load(data_root);
    let Some(account) = state.accounts.iter_mut().find(|a| a.profile_id == profile_id) else {
        return Err(AshError::AccountNotFound { profile_id: profile_id.to_owned() });
    };
    account.ash_account_deleted = deleted;
    if deleted {
        account.ash_account_notice = false;
    }
    save(data_root, &state)?;
    Ok(state)
}

/// Set or clear the "an ash account was made for you" notice.
pub(crate) fn set_ash_account_notice(
    data_root: &Path,
    profile_id: &str,
    pending: bool,
) -> Result<Accounts, AshError> {
    let mut state = load(data_root);
    let Some(account) = state.accounts.iter_mut().find(|a| a.profile_id == profile_id) else {
        return Err(AshError::AccountNotFound { profile_id: profile_id.to_owned() });
    };
    account.ash_account_notice = pending;
    save(data_root, &state)?;
    Ok(state)
}

pub(crate) fn select(data_root: &Path, profile_id: &str) -> Result<Accounts, AshError> {
    let mut state = load(data_root);
    if !state.accounts.iter().any(|a| a.profile_id == profile_id) {
        return Err(AshError::AccountNotFound { profile_id: profile_id.to_owned() });
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
    credentials.delete(&crate::ash_account::session_key(profile_id))?;

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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_skin_recorded_over_http_is_read_back_over_https() {
        let tmp = tempfile::tempdir().unwrap();
        let stored = r#"{"accounts":[{"profile_id":"p","username":"steve","skin_url":"http://textures.minecraft.net/texture/abc","added_at_ms":1}],"active":"p"}"#;
        fs::create_dir_all(tmp.path()).unwrap();
        fs::write(accounts_path(tmp.path()), stored).unwrap();

        let accounts = load(tmp.path());

        assert_eq!(
            accounts.accounts[0].skin_url.as_deref(),
            Some("https://textures.minecraft.net/texture/abc")
        );
        assert_eq!(secure_skin_url("https://elsewhere/x".into()), "https://elsewhere/x");
    }
}
