//! Links the launcher shows that can change without a release (spec 0005):
//! the Discord invite behind "Send feedback", from ash's backend
//! (`GET /v1/links`, ash-backend#19).
//!
//! The last links ash saw are kept, so the link still works while ash's
//! servers can't be reached. Before any are seen there is no link, and the
//! launcher shows none rather than a dead one.

use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest};

const LINKS_FILE: &str = "links.json";

/// Whether the first-run note has been dismissed on this machine.
const NOTE_FILE: &str = "first-run-note-dismissed";

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Links {
    /// A Discord invite, or none.
    pub feedback_url: Option<String>,
}

/// A Discord invite and nothing else, checked here too: the launcher opens it
/// in the player's browser, whatever the backend says.
pub(crate) fn is_invite(url: &str) -> bool {
    let code = url
        .strip_prefix("https://discord.gg/")
        .or_else(|| url.strip_prefix("https://discord.com/invite/"));
    code.is_some_and(|code| {
        (2..=64).contains(&code.len())
            && code.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
    })
}

fn clean(links: Links) -> Links {
    Links { feedback_url: links.feedback_url.filter(|url| is_invite(url)) }
}

fn links_path(data_root: &Path) -> PathBuf {
    data_root.join(LINKS_FILE)
}

/// The links ash last saw, or none.
pub(crate) fn cached(data_root: &Path) -> Links {
    fs::read(links_path(data_root))
        .ok()
        .and_then(|bytes| serde_json::from_slice::<Links>(&bytes).ok())
        .map(clean)
        .unwrap_or_default()
}

/// The links, fresh from ash's backend and kept; or, when it can't be
/// reached, the ones ash last saw.
pub(crate) async fn links(http: &dyn HttpPort, base: &str, data_root: &Path) -> Links {
    let url = format!("{base}/v1/links");
    let fresh = match http.send(HttpRequest::get(&url)).await {
        Ok(response) if response.is_success() => {
            serde_json::from_slice::<Links>(&response.body).ok().map(clean)
        }
        _ => None,
    };
    match fresh {
        Some(links) => {
            // Kept for when ash's servers can't be reached; failing to keep
            // it costs only that.
            if let Ok(encoded) = serde_json::to_vec_pretty(&links) {
                let _ = fs::create_dir_all(data_root)
                    .and_then(|()| fs::write(links_path(data_root), encoded));
            }
            links
        }
        None => cached(data_root),
    }
}

pub(crate) fn note_dismissed(data_root: &Path) -> bool {
    data_root.join(NOTE_FILE).exists()
}

pub(crate) fn dismiss_note(data_root: &Path) -> Result<(), AshError> {
    fs::create_dir_all(data_root).map_err(AshError::writing("creating the data directory"))?;
    fs::write(data_root.join(NOTE_FILE), b"")
        .map_err(AshError::writing("remembering the first-run note"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_a_discord_invite_is_a_feedback_link() {
        assert!(is_invite("https://discord.gg/ash-alpha"));
        assert!(is_invite("https://discord.com/invite/AbC123"));
        for not in [
            "http://discord.gg/ash",
            "https://discord.gg/",
            "https://discord.gg/ash/../../evil",
            "https://evil.example/https://discord.gg/ash",
            "https://discord.gg/ash alpha",
            "javascript:alert(1)",
        ] {
            assert!(!is_invite(not), "{not}");
        }
    }
}
