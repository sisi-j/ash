use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::HttpPort;

pub const VERSION_MANIFEST_URL: &str =
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

/// What Mojang calls a version's `type`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum VersionKind {
    Release,
    Snapshot,
    OldBeta,
    OldAlpha,
    /// Mojang has added types before; an unknown one must not lose the entry.
    Other,
}

impl VersionKind {
    fn parse(raw: &str) -> Self {
        match raw {
            "release" => Self::Release,
            "snapshot" => Self::Snapshot,
            "old_beta" => Self::OldBeta,
            "old_alpha" => Self::OldAlpha,
            _ => Self::Other,
        }
    }
}

/// Whether the data in hand came from Mojang just now, or off disk.
///
/// The UI needs this to be honest with the player: a refresh that silently
/// served stale data would be worse than one that says it could not reach
/// Mojang.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum CatalogueSource {
    Network,
    Cache,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CatalogueEntry {
    pub id: String,
    pub kind: VersionKind,
    /// Mojang's `releaseTime`, kept verbatim as RFC 3339. ash does no date
    /// arithmetic on it, so parsing here would buy nothing and could fail.
    pub released_at: String,
    /// A version target ash treats as first-class, per ADR-0005: 1.8.9 and
    /// the newest 1.21.x release. The UI surfaces these rather than burying
    /// them in nine hundred entries.
    pub first_class: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Catalogue {
    pub source: CatalogueSource,
    /// Milliseconds since the Unix epoch, recorded when the data was fetched
    /// from Mojang - not when it was read off disk. Milliseconds throughout
    /// ash so the UI never has to ask which unit a timestamp is in.
    /// Formatting "3 hours ago" is the UI's job.
    pub fetched_at_ms: u64,
    pub latest_release: String,
    pub latest_snapshot: String,
    pub versions: Vec<CatalogueEntry>,
}

impl Catalogue {
    pub fn releases(&self) -> impl Iterator<Item = &CatalogueEntry> {
        self.versions.iter().filter(|v| v.kind == VersionKind::Release)
    }

    pub fn first_class(&self) -> impl Iterator<Item = &CatalogueEntry> {
        self.versions.iter().filter(|v| v.first_class)
    }
}

// ---- Mojang's wire format -------------------------------------------------

#[derive(Deserialize)]
struct RawManifest {
    latest: RawLatest,
    versions: Vec<RawVersion>,
}

#[derive(Deserialize)]
struct RawLatest {
    release: String,
    snapshot: String,
}

#[derive(Deserialize)]
struct RawVersion {
    id: String,
    #[serde(rename = "type")]
    kind: String,
    #[serde(rename = "releaseTime")]
    release_time: String,
}

// ---- cache ----------------------------------------------------------------

fn cache_path(depot_root: &Path) -> PathBuf {
    depot_root.join("meta").join("version_manifest_v2.json")
}

fn read_cache(depot_root: &Path) -> Option<Catalogue> {
    let raw = fs::read(cache_path(depot_root)).ok()?;
    let mut catalogue: Catalogue = serde_json::from_slice(&raw).ok()?;
    catalogue.source = CatalogueSource::Cache;
    Some(catalogue)
}

fn write_cache(depot_root: &Path, catalogue: &Catalogue) -> Result<(), AshError> {
    let path = cache_path(depot_root);
    let parent = path.parent().expect("cache path always has a parent");
    fs::create_dir_all(parent)
        .map_err(|e| AshError::Storage { detail: format!("creating {}: {e}", parent.display()) })?;
    let encoded = serde_json::to_vec(catalogue)
        .map_err(|e| AshError::Storage { detail: format!("encoding the catalogue: {e}") })?;
    fs::write(&path, encoded)
        .map_err(|e| AshError::Storage { detail: format!("writing {}: {e}", path.display()) })
}

// ---- behaviour ------------------------------------------------------------

/// Newest first is Mojang's own ordering, so the first 1.21.x release we meet
/// is the newest one.
fn mark_first_class(versions: &mut [CatalogueEntry]) {
    let mut seen_modern = false;
    for entry in versions.iter_mut() {
        if entry.kind != VersionKind::Release {
            continue;
        }
        if entry.id == "1.8.9" {
            entry.first_class = true;
        } else if !seen_modern && (entry.id == "1.21" || entry.id.starts_with("1.21.")) {
            entry.first_class = true;
            seen_modern = true;
        }
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or_default()
}

fn parse(body: &[u8]) -> Result<Catalogue, AshError> {
    let raw: RawManifest = serde_json::from_slice(body).map_err(|e| AshError::Malformed {
        url: VERSION_MANIFEST_URL.to_owned(),
        detail: e.to_string(),
    })?;

    let mut versions: Vec<CatalogueEntry> = raw
        .versions
        .into_iter()
        .map(|v| CatalogueEntry {
            id: v.id,
            kind: VersionKind::parse(&v.kind),
            released_at: v.release_time,
            first_class: false,
        })
        .collect();
    mark_first_class(&mut versions);

    Ok(Catalogue {
        source: CatalogueSource::Network,
        fetched_at_ms: now_ms(),
        latest_release: raw.latest.release,
        latest_snapshot: raw.latest.snapshot,
        versions,
    })
}

/// Fetch from Mojang and update the cache.
///
/// If the network fails but a cache exists, the cached catalogue is returned
/// with `source: Cache` rather than an error - a player who is offline should
/// still see their versions. With no cache, the failure propagates.
pub(crate) async fn refresh(
    http: &dyn HttpPort,
    depot_root: &Path,
) -> Result<Catalogue, AshError> {
    let fetched = match http.get(VERSION_MANIFEST_URL).await {
        Ok(response) if response.is_success() => parse(&response.body),
        Ok(response) => Err(AshError::UnexpectedStatus {
            url: VERSION_MANIFEST_URL.to_owned(),
            status: response.status,
        }),
        Err(e) => Err(e),
    };

    match fetched {
        Ok(catalogue) => {
            write_cache(depot_root, &catalogue)?;
            Ok(catalogue)
        }
        Err(e) => read_cache(depot_root).ok_or(e),
    }
}

/// The catalogue, from cache when one exists and from Mojang otherwise.
pub(crate) async fn load(http: &dyn HttpPort, depot_root: &Path) -> Result<Catalogue, AshError> {
    match read_cache(depot_root) {
        Some(catalogue) => Ok(catalogue),
        None => refresh(http, depot_root).await,
    }
}
