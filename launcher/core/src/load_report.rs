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
//! The file sits in a directory the player can open and edit, and some of it
//! goes into ash's own log - which is what a player pastes into a support
//! channel. So what can reach the log is decided by types, not by a filter on
//! the way out: an id or a client version that is not the shape ash's client
//! writes fails to parse, and the whole report is then unreadable; the one
//! free-text field, the display name, goes to the player and never to the log;
//! and an unreadable report is logged by the kind of fault and where it is,
//! never by the parser's message, which quotes what it found.

use std::fmt;
use std::path::Path;
use std::time::UNIX_EPOCH;

use serde::{Deserialize, Serialize};

/// Where the client writes it, relative to the instance's game directory.
pub(crate) const RELATIVE_PATH: &str = "ash/load-report.json";

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub(crate) struct LoadReport {
    client: ClientVersion,
    features: Vec<ReportedFeature>,
    /// Whether any of the player's own mods loaded. Absent in a report from
    /// before the client said, which is a session where they could not have.
    #[serde(default)]
    third_party_mods: bool,
    /// Whose copy of each of ash's bundled mods the loader ran.
    #[serde(default)]
    bundled: Vec<BundledMod>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
struct ReportedFeature {
    id: FeatureId,
    /// What the player calls it, for the notice. Shown, never logged.
    name: String,
    status: FeatureStatus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
enum FeatureStatus {
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

/// A feature's id, in the only shape ash's client writes one:
/// `fps-readout`, `toggle-sprint`. Anything else is not a report from ash.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(try_from = "String")]
struct FeatureId(String);

impl TryFrom<String> for FeatureId {
    type Error = &'static str;

    fn try_from(id: String) -> Result<Self, Self::Error> {
        let well_formed = (1..=64).contains(&id.len())
            && id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-');
        if well_formed {
            Ok(Self(id))
        } else {
            Err("not a feature id ash's client writes")
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
struct BundledMod {
    id: ModId,
    copy: Copy,
}

/// Whose copy of a bundled mod ran.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
enum Copy {
    /// The one ash pinned.
    Ash,
    /// One from the player's own mods folder, which the loader kept in
    /// place of ash's because it was newer.
    Player,
    /// A word a newer client knows and this launcher does not.
    #[serde(other)]
    Other,
}

/// A mod's id, in the shape the loader allows one: `fabric-api`,
/// `legacy-fabric-keybinding-api-v1-common`. Anything else is not a report
/// from ash, and it goes to the log, so it is checked.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(try_from = "String")]
struct ModId(String);

impl TryFrom<String> for ModId {
    type Error = &'static str;

    fn try_from(id: String) -> Result<Self, Self::Error> {
        let well_formed = (1..=64).contains(&id.len())
            && id
                .chars()
                .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-' || c == '_');
        if well_formed {
            Ok(Self(id))
        } else {
            Err("not a mod id")
        }
    }
}

impl ModId {
    /// What to call it in front of a player. Legacy Fabric API is an
    /// aggregator and its modules, and to the player all of it is one thing.
    fn name(&self) -> String {
        match self.0.as_str() {
            "fabric-api" => "Fabric API".to_owned(),
            id if id.starts_with("legacy-fabric-") => "Legacy Fabric API".to_owned(),
            id => id.to_owned(),
        }
    }
}

/// The version of ash's client that wrote the report: `0.1.0`, `0.2.0-dev+abc`.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(try_from = "String")]
struct ClientVersion(String);

impl TryFrom<String> for ClientVersion {
    type Error = &'static str;

    fn try_from(version: String) -> Result<Self, Self::Error> {
        let well_formed = (1..=64).contains(&version.len())
            && version
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '+' | '_'));
        if well_formed {
            Ok(Self(version))
        } else {
            Err("not a version ash's client writes")
        }
    }
}

/// What the player is shown before they play, when a feature degraded.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct DegradationNotice {
    /// The features that did not load, by the name the client gave them.
    pub features: Vec<String>,
    pub message: String,
}

/// A report on disk, and when it was written.
pub(crate) struct Found {
    pub(crate) report: LoadReport,
    /// Milliseconds since the epoch, from the file. `None` where the platform
    /// will not say, in which case the report is taken at its word.
    pub(crate) written_ms: Option<u64>,
}

/// Why a report that exists could not be read - by kind and place only.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Unreadable {
    Io(std::io::ErrorKind),
    Malformed { category: &'static str, line: usize, column: usize },
}

impl fmt::Display for Unreadable {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(kind) => write!(f, "io={kind:?}"),
            Self::Malformed { category, line, column } => {
                write!(f, "malformed={category} line={line} column={column}")
            }
        }
    }
}

impl From<serde_json::Error> for Unreadable {
    fn from(e: serde_json::Error) -> Self {
        // Deliberately not `e.to_string()`: the message quotes what the
        // parser found, and what it found came from a file a player can edit.
        let category = match e.classify() {
            serde_json::error::Category::Io => "io",
            serde_json::error::Category::Syntax => "syntax",
            serde_json::error::Category::Data => "data",
            serde_json::error::Category::Eof => "eof",
        };
        Self::Malformed { category, line: e.line(), column: e.column() }
    }
}

/// The report the client last wrote.
///
/// `Ok(None)` when there is none - the client has not run in this instance
/// yet. `Err` when there is one but it cannot be read, which is worth a line
/// in ash's log and never worth stopping a launch for.
pub(crate) fn read(game_directory: &Path) -> Result<Option<Found>, Unreadable> {
    let path = game_directory.join(RELATIVE_PATH);
    let raw = match std::fs::read(&path) {
        Ok(raw) => raw,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(Unreadable::Io(e.kind())),
    };
    let report: LoadReport = serde_json::from_slice(&raw)?;
    let written_ms = std::fs::metadata(&path)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_millis() as u64);
    Ok(Some(Found { report, written_ms }))
}

/// "A", "A and B", "A, B and C" - the way a sentence names things.
fn listed(names: &[String]) -> String {
    match names {
        [] => String::new(),
        [only] => only.clone(),
        [init @ .., last] => format!("{} and {last}", init.join(", ")),
    }
}

impl LoadReport {
    pub(crate) fn notice(&self) -> Option<DegradationNotice> {
        let features: Vec<String> = self
            .features
            .iter()
            .filter(|f| f.status == FeatureStatus::Degraded)
            .map(|f| f.name.clone())
            .collect();
        // A player's newer copy of a bundled mod, which the loader ran in
        // place of ash's without a word. Named once each, however many of its
        // modules were swapped.
        let mut replaced: Vec<String> = Vec::new();
        for name in self.bundled.iter().filter(|m| m.copy == Copy::Player).map(|m| m.id.name()) {
            if !replaced.contains(&name) {
                replaced.push(name);
            }
        }
        if features.is_empty() && replaced.is_empty() {
            return None;
        }

        let them = if features.len() == 1 { "it" } else { "them" };
        let mut message = match (features.is_empty(), self.third_party_mods) {
            (true, _) => String::new(),
            // With the player's own mods in the game, ash cannot know the
            // fault is its own, and must not say so (ADR-0018).
            (false, true) => format!(
                "{} did not load last time you played. Your own mods were on, so one of them may \
                 be the cause. Turn them off on the instance's page to check: if {them} still \
                 {} not load, an update to ash will fix it.",
                listed(&features),
                if features.len() == 1 { "does" } else { "do" },
            ),
            (false, false) => format!(
                "{} did not load last time you played. That is a problem with ash, not with your \
                 game or your setup, and an update to ash will fix it. You can still play - the \
                 rest of ash works without {them}.",
                listed(&features),
            ),
        };
        if !replaced.is_empty() {
            if !message.is_empty() {
                message.push(' ');
            }
            message.push_str(&format!(
                "Your own copy of {} ran in place of the one ash ships, and ash is not tested with \
                 it.",
                listed(&replaced),
            ));
        }
        Some(DegradationNotice { features, message })
    }

    pub(crate) fn any_degraded(&self) -> bool {
        self.features.iter().any(|f| f.status == FeatureStatus::Degraded)
    }

    /// The features that are on, by the name the client gave them.
    ///
    /// `switch` is a feature's `<id>.enabled` value in the client's settings,
    /// which is newer than the report when the player edits the file between
    /// sessions. A feature with no switch, such as the settings screen, is
    /// not something a player turns on, so it is never listed.
    pub(crate) fn on(&self, switch: impl Fn(&str) -> Option<String>) -> Vec<String> {
        self.features
            .iter()
            .filter(|f| f.status != FeatureStatus::Degraded)
            .filter(|f| match switch(&f.id.0).as_deref() {
                None => false,
                Some("true") => true,
                Some("false") => false,
                // A value the client cannot read, so it fell back to its own
                // default, and the report says which way that went.
                Some(_) => f.status == FeatureStatus::Loaded,
            })
            .map(|f| f.name.clone())
            .collect()
    }

    /// One line for ash's log: `client=0.1.0 fps-readout=loaded toggle-sprint=degraded
    /// third-party-mods=no bundled.fabric-api=ash`.
    ///
    /// Built only from the validated id and version, never from the display
    /// name - which is the one field whose content ash does not constrain.
    pub(crate) fn describe(&self) -> String {
        let mut line = format!("client={}", self.client.0);
        for feature in &self.features {
            let status = match feature.status {
                FeatureStatus::Loaded => "loaded",
                FeatureStatus::Degraded => "degraded",
                FeatureStatus::Off => "off",
                FeatureStatus::Other => "unknown",
            };
            line.push_str(&format!(" {}={status}", feature.id.0));
        }
        line.push_str(if self.third_party_mods {
            " third-party-mods=yes"
        } else {
            " third-party-mods=no"
        });
        for bundled in &self.bundled {
            let copy = match bundled.copy {
                Copy::Ash => "ash",
                Copy::Player => "player",
                Copy::Other => "unknown",
            };
            line.push_str(&format!(" bundled.{}={copy}", bundled.id.0));
        }
        line
    }
}
