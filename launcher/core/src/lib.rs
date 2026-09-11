//! All of ash's launcher behaviour.
//!
//! This crate knows nothing about Tauri, webviews or the UI. It is driven
//! entirely through [`Ash`], which is the single inbound seam: every test in
//! the project exercises the product through this API, and the Tauri layer is
//! a thin adapter over it that holds no logic of its own.
//!
//! Outbound, ash talks to the world only through ports - [`http::HttpPort`]
//! today, a process port when launching arrives. Tests supply fakes, so no
//! test touches the network or spawns a JVM.

mod catalogue;
mod config;
mod error;
mod instance;

pub mod http;

pub use catalogue::{
    Catalogue, CatalogueEntry, CatalogueSource, VersionKind, VERSION_MANIFEST_URL,
};
pub use config::Config;
pub use error::AshError;
pub use instance::{DeletionPreview, Instance, InstanceId};

use std::path::PathBuf;
use std::sync::Arc;

use crate::http::HttpPort;

/// The whole of ash's public API.
pub struct Ash {
    config: Config,
    http: Arc<dyn HttpPort>,
}

impl Ash {
    /// Both collaborators are injected. There is no constructor that reaches
    /// for the real network or a real directory on its own, because that is
    /// what would make the library untestable.
    pub fn new(config: Config, http: Arc<dyn HttpPort>) -> Self {
        Self { config, http }
    }

    pub fn config(&self) -> &Config {
        &self.config
    }

    /// The version catalogue, served from cache when one exists.
    ///
    /// Cheap and offline-safe: this is what the UI calls on open.
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
