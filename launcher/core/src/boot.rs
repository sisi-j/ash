//! How far a starting game has got, read off its own output: the last steps
//! of the LAUNCH bar, between Java starting and the game's window opening.
//!
//! Each step is a line the game prints anyway, so nothing is guessed from
//! time:
//!
//! - Fabric Loader's `Loading N mods:` - the mods are loading;
//! - `Backend library:` (1.20.4, 1.21.11) or `LWJGL Version:` (1.8.9) - the
//!   game is about to open its window, which is where a launch is done: the
//!   rest of the loading shows in the game's own window. Each is printed by
//!   the game's main class just before it creates the window (checked in
//!   each version's jar).
//!
//! Lines are matched inside log4j's XML as well as in plain text, so this
//! reads the raw output, never a rendering of it.

use serde::Serialize;

/// The step a starting game is on, in order.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum BootStage {
    /// The JVM and the loader are starting.
    Starting,
    LoadingMods,
    /// The game's window is opening: the launch is done.
    Ready,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct GameBoot {
    pub stage: BootStage,
    /// How many of the player's own mods this launch loads: the jars switched
    /// on in the instance's `mods` folder, when their mods are on. Not
    /// Fabric Loader's count, which also counts every module of Fabric API,
    /// the game, Java and the libraries inside mods.
    pub players_mods: u32,
}

/// The step the game's output so far shows. Nothing printed yet is starting.
pub(crate) fn read(output: &[String]) -> BootStage {
    let mut stage = BootStage::Starting;
    for line in output {
        if line.contains("Backend library: ") || line.contains("LWJGL Version: ") {
            return BootStage::Ready;
        }
        if mods_loading(line) {
            stage = BootStage::LoadingMods;
        }
    }
    stage
}

/// Fabric Loader's `Loading N mods:`.
fn mods_loading(line: &str) -> bool {
    let Some(at) = line.find("Loading ") else { return false };
    let after = &line[at + "Loading ".len()..];
    after
        .split_once(' ')
        .is_some_and(|(n, rest)| n.parse::<u32>().is_ok() && rest.starts_with("mods"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lines(text: &[&str]) -> Vec<String> {
        text.iter().map(|l| (*l).to_owned()).collect()
    }

    #[test]
    fn a_fabric_game_is_ready_as_its_window_opens() {
        let mut output = lines(&["[18:34:54] [main/INFO]: Loading 61 mods:"]);
        assert_eq!(read(&output), BootStage::LoadingMods);

        output.push("[18:44:55] [Render thread/INFO]: Backend library: LWJGL version 3.3.3".into());
        assert_eq!(read(&output), BootStage::Ready);
    }

    #[test]
    fn a_1_8_9_game_is_ready_as_its_window_opens() {
        let output = lines(&[
            "[16:18:45] [main/INFO]: Loading 10 mods:",
            "[16:18:47] [Client thread/INFO]: LWJGL Version: 2.9.4+legacyfabric.17",
        ]);
        assert_eq!(read(&output), BootStage::Ready);
    }

    #[test]
    fn steps_are_found_inside_log4j_xml() {
        let output = lines(&[
            r#"<log4j:Event logger="FabricLoader" level="INFO" thread="main">"#,
            "  <log4j:Message><![CDATA[Loading 10 mods:",
        ]);
        assert_eq!(read(&output), BootStage::LoadingMods);
    }

    #[test]
    fn nothing_printed_yet_is_starting_and_other_loading_lines_are_not_mods() {
        assert_eq!(read(&[]), BootStage::Starting);
        assert_eq!(read(&lines(&["Loading 3 textures", "Loading mods soon"])), BootStage::Starting);
    }
}
