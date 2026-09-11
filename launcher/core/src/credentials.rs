use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::error::AshError;

/// Where refresh tokens live.
///
/// This is an outbound port for the same reason [`crate::http::HttpPort`] is:
/// the real implementation talks to the operating system, and no test may.
/// Writing a player's Windows Credential Manager during `cargo test` would be
/// both a side effect and a security smell.
pub trait CredentialStore: Send + Sync {
    fn get(&self, key: &str) -> Result<Option<String>, AshError>;
    fn set(&self, key: &str, secret: &str) -> Result<(), AshError>;
    fn delete(&self, key: &str) -> Result<(), AshError>;
}

const SERVICE: &str = "ash-launcher";

/// The real one: Windows Credential Manager, macOS Keychain.
pub struct OsCredentialStore;

impl OsCredentialStore {
    pub fn new() -> Self {
        Self
    }

    fn entry(key: &str) -> Result<keyring::Entry, AshError> {
        keyring::Entry::new(SERVICE, key).map_err(|e| AshError::Credential {
            detail: format!("opening the credential store: {e}"),
        })
    }
}

impl Default for OsCredentialStore {
    fn default() -> Self {
        Self::new()
    }
}

impl CredentialStore for OsCredentialStore {
    fn get(&self, key: &str) -> Result<Option<String>, AshError> {
        match Self::entry(key)?.get_password() {
            Ok(secret) => Ok(Some(secret)),
            Err(keyring::Error::NoEntry) => Ok(None),
            Err(e) => Err(AshError::Credential { detail: format!("reading a credential: {e}") }),
        }
    }

    fn set(&self, key: &str, secret: &str) -> Result<(), AshError> {
        Self::entry(key)?
            .set_password(secret)
            .map_err(|e| AshError::Credential { detail: format!("storing a credential: {e}") })
    }

    fn delete(&self, key: &str) -> Result<(), AshError> {
        match Self::entry(key)?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(e) => Err(AshError::Credential { detail: format!("deleting a credential: {e}") }),
        }
    }
}

/// The fake: in memory, per instance, gone when the test ends.
#[derive(Default)]
pub struct InMemoryCredentialStore {
    secrets: Mutex<HashMap<String, String>>,
}

impl InMemoryCredentialStore {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    /// Every key currently held. Lets a test assert that signing out actually
    /// erased the token rather than merely forgetting the account.
    pub fn keys(&self) -> Vec<String> {
        let mut keys: Vec<String> = self.secrets.lock().unwrap().keys().cloned().collect();
        keys.sort();
        keys
    }
}

impl CredentialStore for InMemoryCredentialStore {
    fn get(&self, key: &str) -> Result<Option<String>, AshError> {
        Ok(self.secrets.lock().unwrap().get(key).cloned())
    }

    fn set(&self, key: &str, secret: &str) -> Result<(), AshError> {
        self.secrets.lock().unwrap().insert(key.to_owned(), secret.to_owned());
        Ok(())
    }

    fn delete(&self, key: &str) -> Result<(), AshError> {
        self.secrets.lock().unwrap().remove(key);
        Ok(())
    }
}
