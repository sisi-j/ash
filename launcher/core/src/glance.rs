//! What the Play page's "This instance" card shows: the ash features that are
//! on, play time, the last session, and the player's mods.
//!
//! Everything here is read, never written. The client's settings and load
//! report are the client's files, and the mods folder is the player's.

use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::path::Path;

use serde::Serialize;

use crate::load_report;
use crate::loader::LoaderPin;

/// Where the client keeps its settings, relative to the game directory.
const CLIENT_SETTINGS: &str = "config/ash.properties";

/// The most of a mod's `fabric.mod.json` read for its name. Real ones are a
/// few kilobytes; this stops a jar that claims otherwise being inflated whole.
const MOD_JSON_LIMIT: u64 = 64 * 1024;

/// An instance, as the Play page shows it beside LAUNCH GAME.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct InstanceGlance {
    pub features: AshFeatures,
    /// Every session added together, a running one up to now.
    pub played_ms: u64,
    pub last_session: Option<LastSession>,
    /// The mods in the instance's mods folder that ash did not put there, by
    /// the name each gives itself, or its file name if it gives none.
    pub mods: Vec<String>,
}

/// Which of ash's features are on.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum AshFeatures {
    /// A vanilla instance, which has no ash client to run them.
    NoClient,
    /// The client has not run here yet to say, so ash does not know.
    NotReported,
    /// By the name the client gives each. Empty when the player has switched
    /// every one off.
    On { features: Vec<String> },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct LastSession {
    pub started_ms: u64,
    /// `None` while the game is running.
    pub ended_ms: Option<u64>,
}

pub(crate) fn features(game_directory: &Path) -> AshFeatures {
    // An unreadable report is the notice's business, and ash's log hears
    // about it from there. Here it only means ash does not know.
    let Ok(Some(found)) = load_report::read(game_directory) else {
        return AshFeatures::NotReported;
    };
    // The client writes its settings on its first run, before its first
    // report. Without them there is no telling which features are switches.
    let Some(switches) = switches(game_directory) else {
        return AshFeatures::NotReported;
    };
    AshFeatures::On { features: found.report.on(|id| switches.get(id).cloned()) }
}

/// Each `<id>.enabled` value in the client's settings, by feature id.
fn switches(game_directory: &Path) -> Option<HashMap<String, String>> {
    let raw = fs::read_to_string(game_directory.join(CLIENT_SETTINGS)).ok()?;
    Some(
        properties(&raw)
            .into_iter()
            .filter_map(|(key, value)| Some((key.strip_suffix(".enabled")?.to_owned(), value)))
            .collect(),
    )
}

/// The keys and values of a Java properties file, as far as ash's own
/// settings need: comments, `=`, `:` or whitespace between a key and its
/// value, and continued lines skipped rather than misread as keys. The keys
/// ash reads have no escapes in them, so escapes are left as they are.
fn properties(raw: &str) -> Vec<(String, String)> {
    let mut pairs = Vec::new();
    let mut continued = false;
    for line in raw.lines() {
        let line = line.trim_start();
        let was_continued = continued;
        // A line ending in an odd number of backslashes runs on to the next.
        continued = line.chars().rev().take_while(|&c| c == '\\').count() % 2 == 1;
        if was_continued || line.is_empty() || line.starts_with('#') || line.starts_with('!') {
            continue;
        }
        let end = line.find(|c: char| c == '=' || c == ':' || c.is_whitespace());
        let (key, rest) = line.split_at(end.unwrap_or(line.len()));
        let rest = rest.trim_start();
        let value = rest.strip_prefix(['=', ':']).unwrap_or(rest).trim();
        pairs.push((key.to_owned(), value.to_owned()));
    }
    pairs
}

/// The player's mods, leaving out ash's own client and bundled mods.
pub(crate) fn mods(game_directory: &Path, pin: Option<&LoaderPin>) -> Vec<String> {
    // A vanilla game loads nothing from the folder, whatever is in it.
    let Some(pin) = pin else {
        return Vec::new();
    };
    let ashs: Vec<String> = pin
        .client_jar
        .map(str::to_owned)
        .into_iter()
        .chain(pin.bundled_mods.iter().filter_map(|m| m.file_name().ok()))
        .collect();

    let mut mods: Vec<String> = fs::read_dir(game_directory.join("mods"))
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.path().is_file())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter(|name| name.to_ascii_lowercase().ends_with(".jar"))
        .filter(|name| !ashs.contains(name))
        .map(|name| {
            let path = game_directory.join("mods").join(&name);
            mod_name(&path).unwrap_or_else(|| name[..name.len() - ".jar".len()].to_owned())
        })
        .collect();
    mods.sort_by_key(|name| name.to_lowercase());
    mods
}

/// The name a Fabric or Legacy Fabric mod gives itself in `fabric.mod.json`.
fn mod_name(jar: &Path) -> Option<String> {
    let mut archive = zip::ZipArchive::new(fs::File::open(jar).ok()?).ok()?;
    let mut raw = Vec::new();
    archive.by_name("fabric.mod.json").ok()?.take(MOD_JSON_LIMIT).read_to_end(&mut raw).ok()?;
    let json: serde_json::Value = serde_json::from_slice(&raw).ok()?;
    let name = json.get("name")?.as_str()?.trim();
    (!name.is_empty()).then(|| name.to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn properties_read_the_way_java_reads_them() {
        let raw =
            "# a comment\n! another\n\nfps-readout.enabled = false\ncrosshair.enabled:true\n  \
                   hit-indicator.enabled\ttrue  \n";

        assert_eq!(
            properties(raw),
            [
                ("fps-readout.enabled".to_owned(), "false".to_owned()),
                ("crosshair.enabled".to_owned(), "true".to_owned()),
                ("hit-indicator.enabled".to_owned(), "true".to_owned()),
            ]
        );
    }

    #[test]
    fn a_continued_line_is_not_read_as_a_key() {
        let raw = "note = one \\\n  crosshair.enabled = false\ncrosshair.enabled = true\n";

        let pairs = properties(raw);

        assert_eq!(pairs.len(), 2);
        assert_eq!(pairs[1], ("crosshair.enabled".to_owned(), "true".to_owned()));
    }
}
