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

pub mod http;

pub use catalogue::{ManifestProbe, VERSION_MANIFEST_URL};
pub use config::Config;
pub use error::AshError;

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

    /// Read the headline numbers from Mojang's version manifest.
    pub async fn probe_manifest(&self) -> Result<ManifestProbe, AshError> {
        catalogue::probe(self.http.as_ref()).await
    }
}
