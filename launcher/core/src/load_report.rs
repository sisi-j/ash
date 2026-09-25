//! What the client says about its last session: which of ash's features
//! loaded, and which degraded.
//!
//! When one of the client's features cannot load - a mixin that no longer
//! matches its target after a game update, say - the game runs without it,
//! and the client writes this report as it starts. The launcher reads it
//! before the next play and tells the player, in words that make it ash's
//! problem rather than theirs. See ADR-0017.
//!
//! The channel runs one way. The client writes `ash/load-report.json` in the
//! instance's game directory; the launcher reads it and never writes it, just
//! as it never writes the client's settings. A first launch reports nothing,
//! because the client has not run yet to say - the accepted cost of reporting
//! here, where the player can still act, rather than in game.
//!
//! The file sits in a directory the player can open, so everything read from
//! it is treated as data from outside: a status this launcher does not know is
//! kept rather than rejected, and nothing from it reaches ash's log unless it
//! looks like something ash's own client could have written.

use std::path::Path;

use serde::{Deserialize, Serialize};

/// Where the client writes it, relative to the instance's game directory.
pub(crate) const RELATIVE_PATH: &str = "ash/load-report.json";

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub(crate) struct LoadReport {
    /// The version of ash's client that wrote it.
    pub(crate) client: String,
    pub(crate) features: Vec<FeatureStatus>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub(crate) struct FeatureStatus {
    pub(crate) id: String,
    /// What the player calls it, from the client, for the notice.
    pub(crate) name: String,
    pub(crate) status: Status,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum Status {
    Loaded,
    /// Its mixins did not apply, so the game ran without it.
    Degraded,
    /// The player switched it off in the client's settings. Not a problem.
    Off,
    /// A status a newer client knows and this launcher does not. Kept as a
    /// value rather than an error, so one unfamiliar word cannot throw away
    /// the rest of the report - including a feature that did degrade.
    #[serde(other)]
    Other,
}

/// What the player is shown before they play, when a feature degraded.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct DegradationNotice {
    /// The features that did not load, by the name the client gave them.
    pub features: Vec<String>,
    pub message: String,
}

/// The report the client last wrote.
///
/// `Ok(None)` when there is none - the client has not run in this instance
/// yet. `Err` with a reason when there is one but it cannot be read, which is
/// worth a line in ash's log and never worth stopping a launch for.
pub(crate) fn read(game_directory: &Path) -> Result<Option<LoadReport>, String> {
    let raw = match std::fs::read(game_directory.join(RELATIVE_PATH)) {
        Ok(raw) => raw,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(e.to_string()),
    };
    serde_json::from_slice(&raw).map(Some).map_err(|e| e.to_string())
}

/// "A", "A and B", "A, B and C" - the way a sentence names things.
fn listed(names: &[String]) -> String {
    match names {
        [] => String::new(),
        [only] => only.clone(),
        [init @ .., last] => format!("{} and {last}", init.join(", ")),
    }
}

/// Only what ash's own client writes - lowercase ids, digits, dots, dashes -
/// and not much of it. Anything else in the file came from somewhere else,
/// and has no business in a log a player will paste into a support channel.
fn loggable(value: &str) -> String {
    value
        .chars()
        .filter(|c| {
            c.is_ascii_lowercase() || c.is_ascii_digit() || matches!(c, '.' | '-' | '_' | '+')
        })
        .take(64)
        .collect()
}

impl LoadReport {
    pub(crate) fn notice(&self) -> Option<DegradationNotice> {
        let features: Vec<String> = self
            .features
            .iter()
            .filter(|f| f.status == Status::Degraded)
            .map(|f| f.name.clone())
            .collect();
        if features.is_empty() {
            return None;
        }
        let message = format!(
            "{} did not load last time you played. That is a problem with ash, not with your game \
             or your setup, and an update to ash will fix it. You can still play - the rest of \
             ash works without {}.",
            listed(&features),
            if features.len() == 1 { "it" } else { "them" },
        );
        Some(DegradationNotice { features, message })
    }

    pub(crate) fn any_degraded(&self) -> bool {
        self.features.iter().any(|f| f.status == Status::Degraded)
    }

    /// One line for ash's log: `client=0.1.0 fps-readout=loaded toggle-sprint=degraded`.
    pub(crate) fn describe(&self) -> String {
        let mut line = format!("client={}", loggable(&self.client));
        for feature in &self.features {
            let status = match feature.status {
                Status::Loaded => "loaded",
                Status::Degraded => "degraded",
                Status::Off => "off",
                Status::Other => "unknown",
            };
            line.push_str(&format!(" {}={status}", loggable(&feature.id)));
        }
        line
    }
}
