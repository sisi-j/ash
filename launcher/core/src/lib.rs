//! All of ash's launcher behaviour.
//!
//! This crate knows nothing about Tauri, webviews or the UI. It is driven
//! entirely through [`Ash`], which is the single inbound seam: every test in
//! the project exercises the product through this API, and the Tauri layer is
//! a thin adapter over it that holds no logic of its own.
//!
//! Outbound, ash talks to the world only through ports - [`http::HttpPort`]
//! for the network and [`credentials::CredentialStore`] for the OS credential
//! store, with a process port to come when launching arrives. Tests supply
//! fakes, so no test touches the network, the credential store, or spawns a
//! JVM.

mod account;
mod auth;
mod catalogue;
mod config;
mod error;
mod instance;

pub mod credentials;
pub mod http;

pub use account::{Account, Accounts};
pub use catalogue::{
    Catalogue, CatalogueEntry, CatalogueSource, VersionKind, VERSION_MANIFEST_URL,
};
pub use config::Config;
pub use error::AshError;
pub use instance::{DeletionPreview, Instance, InstanceId};

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Serialize;

use crate::credentials::CredentialStore;
use crate::http::HttpPort;

/// A sign-in waiting on the player, as the UI needs to see it.
///
/// The device code itself is deliberately absent: it stays inside ash-core,
/// so the only thing crossing to the UI is what a player must read off the
/// screen and type into a browser.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct PendingSignIn {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_at_ms: u64,
    pub interval_secs: u64,
}

/// The result of one poll.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "status", rename_all = "snake_case")]
pub enum SignInStatus {
    /// Ask again in this many seconds. The service can raise the interval.
    Waiting { interval_secs: u64 },
    Complete { account: Account },
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or_default()
}

/// The whole of ash's public API.
pub struct Ash {
    config: Config,
    http: Arc<dyn HttpPort>,
    credentials: Arc<dyn CredentialStore>,
    client_id: String,
    /// One sign-in at a time. The device code lives here rather than
    /// travelling to the UI and back.
    pending: Mutex<Option<auth::Pending>>,
}

impl Ash {
    /// Every collaborator is injected. There is no constructor that reaches
    /// for the real network, the real credential store, or a real directory
    /// on its own, because that is what would make the library untestable.
    pub fn new(
        config: Config,
        http: Arc<dyn HttpPort>,
        credentials: Arc<dyn CredentialStore>,
        client_id: impl Into<String>,
    ) -> Self {
        Self {
            config,
            http,
            credentials,
            client_id: client_id.into(),
            pending: Mutex::new(None),
        }
    }

    pub fn config(&self) -> &Config {
        &self.config
    }

    // ---- catalogue --------------------------------------------------------

    /// The version catalogue, served from cache when one exists.
    pub async fn catalogue(&self) -> Result<Catalogue, AshError> {
        catalogue::load(self.http.as_ref(), &self.config.depot_root).await
    }

    /// Fetch the catalogue from Mojang and update the cache.
    ///
    /// Falls back to the cache if Mojang cannot be reached, flagging the
    /// result as [`CatalogueSource::Cache`] so the UI can say so rather than
    /// pretending the refresh worked.
    pub async fn refresh_catalogue(&self) -> Result<Catalogue, AshError> {
        catalogue::refresh(self.http.as_ref(), &self.config.depot_root).await
    }

    // ---- sign-in ----------------------------------------------------------

    /// Start a device-code sign-in.
    ///
    /// Microsoft's codes expire in about fifteen minutes and the docs say to
    /// request one only when the player is ready, so this is called on the
    /// click and not on app open.
    pub async fn begin_sign_in(&self) -> Result<PendingSignIn, AshError> {
        let pending = auth::begin(self.http.as_ref(), &self.client_id, now_ms()).await?;
        let view = PendingSignIn {
            user_code: pending.user_code.clone(),
            verification_uri: pending.verification_uri.clone(),
            expires_at_ms: pending.expires_at_ms,
            interval_secs: pending.interval_secs,
        };
        *self.pending.lock().unwrap() = Some(pending);
        Ok(view)
    }

    /// Ask whether the player has approved it yet.
    ///
    /// The caller waits `interval_secs` between calls; the service can raise
    /// that by answering `slow_down`, which is honoured rather than treated
    /// as a failure.
    pub async fn poll_sign_in(&self) -> Result<SignInStatus, AshError> {
        let pending = self.pending.lock().unwrap().clone().ok_or(AshError::NoSignInPending)?;

        match auth::poll(self.http.as_ref(), &self.client_id, &pending, now_ms()).await {
            Ok(auth::Poll::Waiting { interval_secs }) => {
                Ok(SignInStatus::Waiting { interval_secs })
            }
            Ok(auth::Poll::Complete(session)) => {
                *self.pending.lock().unwrap() = None;
                let account = account::upsert(
                    &self.config.data_root,
                    self.credentials.as_ref(),
                    &session.profile_id,
                    &session.username,
                    session.skin_url.clone(),
                    session.refresh_token.as_deref(),
                )?;
                Ok(SignInStatus::Complete { account })
            }
            Err(e) => {
                // A terminated sign-in cannot be polled again. Clearing it
                // means the next poll says so plainly instead of replaying
                // the same failure forever.
                *self.pending.lock().unwrap() = None;
                Err(e)
            }
        }
    }

    /// Abandon a sign-in in progress.
    pub fn cancel_sign_in(&self) {
        *self.pending.lock().unwrap() = None;
    }

    // ---- accounts ---------------------------------------------------------

    pub fn accounts(&self) -> Accounts {
        account::load(&self.config.data_root)
    }

    pub fn select_account(&self, profile_id: &str) -> Result<Accounts, AshError> {
        account::select(&self.config.data_root, profile_id)
    }

    /// Forget an account and erase its stored refresh token.
    pub fn remove_account(&self, profile_id: &str) -> Result<Accounts, AshError> {
        account::remove(&self.config.data_root, self.credentials.as_ref(), profile_id)
    }

    /// Exchange the stored refresh token for a usable Minecraft session.
    ///
    /// Called before a launch. A refresh token Microsoft rejects is dead, and
    /// surfaces as [`AshError::SessionExpired`] so the UI can ask for a fresh
    /// sign-in rather than failing the launch with something cryptic.
    pub async fn ensure_session(&self, profile_id: &str) -> Result<Account, AshError> {
        let token = account::refresh_token(self.credentials.as_ref(), profile_id)?;
        let session = auth::refresh(self.http.as_ref(), &self.client_id, &token).await?;
        account::upsert(
            &self.config.data_root,
            self.credentials.as_ref(),
            &session.profile_id,
            &session.username,
            session.skin_url.clone(),
            session.refresh_token.as_deref(),
        )
    }

    // ---- instances --------------------------------------------------------
    //
    // These touch only the filesystem, so they are honestly synchronous
    // rather than async for symmetry. The adapter puts them on Tauri's async
    // runtime so a large directory walk cannot stall the window.

    /// Create an instance for a version target, with its own game directory.
    pub fn create_instance(&self, name: &str, version_id: &str) -> Result<Instance, AshError> {
        instance::create(&self.config.instances_root, name, version_id)
    }

    /// Every instance, most recently played first.
    pub fn instances(&self) -> Result<Vec<Instance>, AshError> {
        instance::list(&self.config.instances_root)
    }

    pub fn instance(&self, id: &InstanceId) -> Result<Instance, AshError> {
        instance::get(&self.config.instances_root, id)
    }

    /// Change the label. The id and every path stay put.
    pub fn rename_instance(&self, id: &InstanceId, name: &str) -> Result<Instance, AshError> {
        instance::rename(&self.config.instances_root, id, name)
    }

    pub fn mark_played(&self, id: &InstanceId) -> Result<Instance, AshError> {
        instance::mark_played(&self.config.instances_root, id)
    }

    /// What deleting this instance would destroy, worlds listed by name.
    pub fn preview_deletion(&self, id: &InstanceId) -> Result<DeletionPreview, AshError> {
        instance::preview_deletion(&self.config.instances_root, id)
    }

    /// Delete an instance and everything inside it. Never touches the depot.
    pub fn delete_instance(&self, id: &InstanceId) -> Result<(), AshError> {
        instance::delete(&self.config.instances_root, id)
    }

    /// The instance's game directory, for revealing in the file manager.
    pub fn game_directory(&self, id: &InstanceId) -> PathBuf {
        instance::game_dir(&self.config.instances_root, id)
    }
}
