//! The launcher's News page (spec 0004): published posts from ash's backend,
//! the last copy kept for when it can't be reached, and whether there is a
//! post the player hasn't seen.
//!
//! Like the ash account, never an error: no news is a page that says so, not
//! a failure over the launcher.

use std::fs;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest};

const NEWS_FILE: &str = "news.json";
const SEEN_FILE: &str = "news-seen.json";

/// One published post, as `GET /v1/news` describes it (ash-backend's
/// `contract/news.json`).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct NewsPost {
    pub id: String,
    pub kind: NewsKind,
    pub title: String,
    /// The agreed Markdown subset. The launcher renders it without HTML.
    pub body: String,
    pub cover_url: Option<String>,
    pub published_at: u64,
    pub updated_at: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum NewsKind {
    News,
    PatchNotes,
}

/// What the News page shows.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct News {
    /// Newest first.
    pub posts: Vec<NewsPost>,
    /// A post newer than the last one the player saw.
    pub unread: bool,
    /// ash's servers couldn't be reached; these are the last posts it saw.
    pub offline: bool,
}

#[derive(Serialize, Deserialize)]
struct Feed {
    posts: Vec<NewsPost>,
}

#[derive(Serialize, Deserialize)]
struct Seen {
    /// The newest `published_at` the player has seen.
    published_at: u64,
}

fn read<T: serde::de::DeserializeOwned>(path: &Path) -> Option<T> {
    serde_json::from_slice(&fs::read(path).ok()?).ok()
}

fn write(path: &Path, value: &impl Serialize) -> Result<(), AshError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(AshError::writing("creating the data directory"))?;
    }
    let encoded = serde_json::to_vec_pretty(value)
        .map_err(|e| AshError::Storage { detail: format!("encoding news: {e}") })?;
    fs::write(path, encoded).map_err(AshError::writing("writing news"))
}

fn news_path(data_root: &Path) -> PathBuf {
    data_root.join(NEWS_FILE)
}

fn seen_path(data_root: &Path) -> PathBuf {
    data_root.join(SEEN_FILE)
}

async fn fetch(http: &dyn HttpPort, base: &str) -> Result<Vec<NewsPost>, AshError> {
    let url = format!("{base}/v1/news");
    let response = http.send(HttpRequest::get(&url)).await?;
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url, status: response.status });
    }
    let feed: Feed = serde_json::from_slice(&response.body)
        .map_err(|e| AshError::Malformed { url, detail: e.to_string() })?;
    Ok(feed.posts)
}

pub(crate) async fn news(http: &dyn HttpPort, base: &str, data_root: &Path) -> News {
    let (posts, offline) = match fetch(http, base).await {
        Ok(posts) => {
            // Kept for next time ash can't reach its servers. A failure to
            // keep it costs only that, so it is not an error here.
            let _ = write(&news_path(data_root), &Feed { posts: posts.clone() });
            (posts, false)
        }
        Err(_) => (read::<Feed>(&news_path(data_root)).map(|f| f.posts).unwrap_or_default(), true),
    };
    let seen = read::<Seen>(&seen_path(data_root)).map(|s| s.published_at).unwrap_or(0);
    let unread = posts.iter().any(|p| p.published_at > seen);
    News { posts, unread, offline }
}

/// The player opened the News page: everything ash last showed is seen.
///
/// Recorded as the newest post's own time rather than the clock's, so a
/// machine with its clock wrong can neither hide a new post nor keep an old
/// one unread forever.
pub(crate) fn mark_seen(data_root: &Path) -> Result<(), AshError> {
    let newest = read::<Feed>(&news_path(data_root))
        .and_then(|f| f.posts.iter().map(|p| p.published_at).max())
        .unwrap_or(0);
    let current = read::<Seen>(&seen_path(data_root)).map(|s| s.published_at).unwrap_or(0);
    write(&seen_path(data_root), &Seen { published_at: newest.max(current) })
}
