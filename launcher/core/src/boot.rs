//! How far a starting game has got, read off its own output: the last steps
//! of the LAUNCH bar, between Java starting and the title screen.
//!
//! Each step is a line the game prints anyway (checked in 1.8.9, 1.20.4 and
//! 1.21.11 logs), so nothing is guessed from time:
//!
//! - Fabric Loader's `Loading N mods:` - the mods are loading;
//! - `Reloading ResourceManager:` - the window is up and resources are loading;
//! - the ash client's `ash: title screen N ms` - the title screen is up. A
//!   vanilla instance has no ash client, so for it `Sound engine started`,
//!   among the last lines of the first load, stands in.
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
    LoadingResources,
    /// The title screen is up.
    Ready,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct GameBoot {
    pub stage: BootStage,
    /// How many mods Fabric Loader said it is loading, ash's own among them.
    pub mods: Option<u32>,
}

impl GameBoot {
    pub(crate) const STARTING: GameBoot = GameBoot { stage: BootStage::Starting, mods: None };

    /// The further of two readings. The output kept is only its tail, so a
    /// step once seen stays seen after its line has scrolled out.
    pub(crate) fn furthest(self, other: GameBoot) -> GameBoot {
        GameBoot { stage: self.stage.max(other.stage), mods: self.mods.or(other.mods) }
    }
}

/// How far the game has got, by its output so far.
pub(crate) fn read(output: &[String], ash_client: bool) -> GameBoot {
    let mut boot = GameBoot::STARTING;
    for line in output {
        if let Some(n) = mods_loading(line) {
            boot.mods = Some(n);
            boot.stage = boot.stage.max(BootStage::LoadingMods);
        }
        if line.contains("Reloading ResourceManager") {
            boot.stage = boot.stage.max(BootStage::LoadingResources);
        }
        let ready = if ash_client {
            line.contains("ash: title screen")
        } else {
            line.contains("Sound engine started")
        };
        if ready {
            boot.stage = BootStage::Ready;
        }
    }
    boot
}

/// N, from Fabric Loader's `Loading N mods:`.
fn mods_loading(line: &str) -> Option<u32> {
    let after = &line[line.find("Loading ")? + "Loading ".len()..];
    let (n, rest) = after.split_once(' ')?;
    rest.starts_with("mods").then(|| n.parse().ok()).flatten()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lines(text: &[&str]) -> Vec<String> {
        text.iter().map(|l| (*l).to_owned()).collect()
    }

    #[test]
    fn a_fabric_game_walks_through_each_step() {
        let mut output = lines(&["[18:44:45] [main/INFO]: Loading 50 mods:"]);
        assert_eq!(read(&output, true), GameBoot { stage: BootStage::LoadingMods, mods: Some(50) });

        output.push(
            "[18:45:03] [Render thread/INFO]: Reloading ResourceManager: vanilla, ash".into(),
        );
        output.push("[18:45:05] [Render thread/INFO]: Sound engine started".into());
        assert_eq!(
            read(&output, true).stage,
            BootStage::LoadingResources,
            "an ash game waits for ash's line"
        );

        output.push("ash: title screen 21480 ms after the game started".into());
        assert_eq!(read(&output, true).stage, BootStage::Ready);
    }

    #[test]
    fn steps_are_found_inside_log4j_xml() {
        let output = lines(&[
            r#"<log4j:Event logger="FabricLoader" level="INFO" thread="main">"#,
            "  <log4j:Message><![CDATA[Loading 10 mods:",
        ]);
        assert_eq!(read(&output, true).mods, Some(10));
    }

    #[test]
    fn a_vanilla_game_is_ready_when_its_sound_starts() {
        let output = lines(&[
            "[16:18:48] [Client thread/INFO]: Reloading ResourceManager: Default",
            "[16:18:48] [Sound Library Loader/INFO]: Sound engine started",
        ]);
        assert_eq!(read(&output, false), GameBoot { stage: BootStage::Ready, mods: None });
    }

    #[test]
    fn nothing_printed_yet_is_starting_and_other_loading_lines_are_not_mods() {
        assert_eq!(read(&[], true), GameBoot::STARTING);
        let output = lines(&["Loading 3 textures", "Loading mods soon"]);
        assert_eq!(read(&output, true), GameBoot::STARTING);
    }

    #[test]
    fn a_step_once_seen_stays_seen() {
        let earlier = GameBoot { stage: BootStage::LoadingResources, mods: Some(50) };
        assert_eq!(GameBoot::STARTING.furthest(earlier), earlier);
    }
}
