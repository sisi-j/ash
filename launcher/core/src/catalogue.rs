use serde::de::IgnoredAny;
use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::HttpPort;

pub const VERSION_MANIFEST_URL: &str =
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

/// The narrowest useful read of Mojang's version manifest.
///
/// Deliberately not the catalogue itself - #3 builds that, with caching,
/// release/snapshot filtering and offline behaviour. This exists so the
/// walking skeleton carries a real response along the whole seam.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct ManifestProbe {
    pub total_versions: usize,
    pub latest_release: String,
    pub latest_snapshot: String,
}

#[derive(Deserialize)]
struct RawManifest {
    latest: RawLatest,
    /// Counted, never inspected - `IgnoredAny` skips the bodies entirely.
    versions: Vec<IgnoredAny>,
}

#[derive(Deserialize)]
struct RawLatest {
    release: String,
    snapshot: String,
}

pub(crate) async fn probe(http: &dyn HttpPort) -> Result<ManifestProbe, AshError> {
    let response = http.get(VERSION_MANIFEST_URL).await?;

    if !response.is_success() {
        return Err(AshError::UnexpectedStatus {
            url: VERSION_MANIFEST_URL.to_owned(),
            status: response.status,
        });
    }

    let raw: RawManifest = serde_json::from_slice(&response.body).map_err(|e| {
        AshError::Malformed { url: VERSION_MANIFEST_URL.to_owned(), detail: e.to_string() }
    })?;

    Ok(ManifestProbe {
        total_versions: raw.versions.len(),
        latest_release: raw.latest.release,
        latest_snapshot: raw.latest.snapshot,
    })
}
