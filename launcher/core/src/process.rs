//! The process port: how ash starts the game and watches it.
//!
//! ash-core never calls `std::process::Command` directly. Launching is the
//! one operation whose real effect - a JVM with a window - cannot be undone
//! or inspected by a test, so it goes behind a port like the network does.
//! The fake records the invocation, which is what the launch tests assert.

use std::collections::VecDeque;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use serde::Serialize;

use crate::error::AshError;

/// How many lines of game output ash keeps.
///
/// Enough to hold a crash report and the lines that led to it. Unbounded
/// would let a chatty mod pack grow ash's memory for as long as the game
/// runs, and nobody reads the first line of a two-hour session.
const LOG_LINES: usize = 1000;

/// Exactly what ash would run. Assembled and inspectable before anything
/// spawns.
///
/// Deliberately not `Serialize`. `args` carries a Minecraft access token, so
/// the only shape that can cross to the UI is [`InvocationView`], which is
/// redacted by construction.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Invocation {
    pub program: PathBuf,
    pub args: Vec<String>,
    pub working_directory: PathBuf,
    /// Exact strings to blank out before this is shown to anyone.
    pub(crate) secrets: Vec<String>,
}

/// An invocation safe to display, log, or send to the UI.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct InvocationView {
    pub program: String,
    pub args: Vec<String>,
    pub working_directory: String,
}

impl Invocation {
    /// The same command with every secret replaced.
    ///
    /// Whole-argument comparison, not a substring scan: an access token is
    /// passed as its own argument, and a substring replace over a command
    /// line is the kind of thing that silently stops matching.
    pub fn view(&self) -> InvocationView {
        InvocationView {
            program: self.program.display().to_string(),
            args: self
                .args
                .iter()
                .map(|arg| {
                    // An empty secret would match every empty argument, and
                    // `--xuid ""` is a real thing ash passes.
                    if self.secrets.iter().any(|secret| !secret.is_empty() && secret == arg) {
                        "<redacted>".to_owned()
                    } else {
                        arg.clone()
                    }
                })
                .collect(),
            working_directory: self.working_directory.display().to_string(),
        }
    }
}

/// How the game ended.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum GameStatus {
    /// Started, still running.
    Running,
    /// Exited. `code` is absent when a signal ended it.
    Exited { code: Option<i32>, clean: bool },
}

/// A game process ash started.
pub trait GameProcess: Send + Sync {
    /// Never blocks. The UI asks repeatedly; ash must stay responsive while
    /// the game runs.
    fn status(&self) -> GameStatus;
    /// The tail of the game's own output, kept after it exits so a crash can
    /// be read.
    fn log(&self) -> Vec<String>;
    fn stop(&self);
}

/// Starting the game.
pub trait ProcessPort: Send + Sync {
    fn spawn(&self, invocation: &Invocation) -> Result<Box<dyn GameProcess>, AshError>;
}

// ---- the real one ----------------------------------------------------------

/// Spawns a real JVM.
#[derive(Debug, Default, Clone, Copy)]
pub struct OsProcessPort;

impl OsProcessPort {
    pub fn new() -> Self {
        Self
    }
}

impl ProcessPort for OsProcessPort {
    fn spawn(&self, invocation: &Invocation) -> Result<Box<dyn GameProcess>, AshError> {
        use std::process::{Command, Stdio};

        let mut command = Command::new(&invocation.program);
        command
            .args(&invocation.args)
            .current_dir(&invocation.working_directory)
            // The game inherits nothing to read from. A JVM that blocks on
            // stdin behind a GUI launcher would hang with nothing to show.
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());

        // Without this, java.exe opens a console window beside the game.
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            const CREATE_NO_WINDOW: u32 = 0x0800_0000;
            command.creation_flags(CREATE_NO_WINDOW);
        }

        let mut child = command.spawn().map_err(|e| AshError::LaunchFailed {
            detail: format!("starting the Java runtime: {e}"),
        })?;

        let log = Arc::new(Mutex::new(VecDeque::with_capacity(LOG_LINES)));

        // Both pipes are drained on their own threads. A child whose output
        // pipe fills up blocks forever, so this is not optional - and it is
        // also what makes the log available after a crash.
        if let Some(stdout) = child.stdout.take() {
            drain(stdout, Arc::clone(&log));
        }
        if let Some(stderr) = child.stderr.take() {
            drain(stderr, Arc::clone(&log));
        }

        Ok(Box::new(OsGameProcess { child: Mutex::new(child), log }))
    }
}

fn drain<R: std::io::Read + Send + 'static>(reader: R, log: Arc<Mutex<VecDeque<String>>>) {
    std::thread::spawn(move || {
        use std::io::BufRead;
        for line in std::io::BufReader::new(reader).lines().map_while(Result::ok) {
            let mut log = log.lock().unwrap();
            if log.len() == LOG_LINES {
                log.pop_front();
            }
            log.push_back(line);
        }
    });
}

struct OsGameProcess {
    child: Mutex<std::process::Child>,
    log: Arc<Mutex<VecDeque<String>>>,
}

impl GameProcess for OsGameProcess {
    fn status(&self) -> GameStatus {
        match self.child.lock().unwrap().try_wait() {
            Ok(Some(status)) => {
                let code = status.code();
                // Exit code 0 is a clean quit. Anything else - and any exit
                // with no code at all, which on Unix means a signal - is a
                // crash, and the player should be told so rather than shown
                // the same "finished" either way.
                GameStatus::Exited { code, clean: code == Some(0) }
            }
            Ok(None) => GameStatus::Running,
            // The handle is unusable, so the honest answer is that it is no
            // longer running and we cannot say why.
            Err(_) => GameStatus::Exited { code: None, clean: false },
        }
    }

    fn log(&self) -> Vec<String> {
        self.log.lock().unwrap().iter().cloned().collect()
    }

    fn stop(&self) {
        let _ = self.child.lock().unwrap().kill();
    }
}

// ---- the fake --------------------------------------------------------------

/// Records what would have been run, and never runs it.
#[derive(Default)]
pub struct FakeProcessPort {
    spawned: Mutex<Vec<Invocation>>,
    /// What every spawned process reports. Tests set this to drive the
    /// running/exited/crashed paths.
    outcome: Mutex<Option<GameStatus>>,
    log: Mutex<Vec<String>>,
    fail_with: Mutex<Option<String>>,
}

impl FakeProcessPort {
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            spawned: Mutex::new(Vec::new()),
            outcome: Mutex::new(Some(GameStatus::Running)),
            log: Mutex::new(Vec::new()),
            fail_with: Mutex::new(None),
        })
    }

    /// Every invocation spawned so far, in order.
    pub fn spawned(&self) -> Vec<Invocation> {
        self.spawned.lock().unwrap().clone()
    }

    /// The most recent invocation. Panics if nothing was spawned, because a
    /// test asking for it has already decided one should have been.
    pub fn last(&self) -> Invocation {
        self.spawned.lock().unwrap().last().cloned().expect("nothing was spawned")
    }

    pub fn set_status(self: &Arc<Self>, status: GameStatus) -> Arc<Self> {
        *self.outcome.lock().unwrap() = Some(status);
        Arc::clone(self)
    }

    pub fn set_log(self: &Arc<Self>, lines: &[&str]) -> Arc<Self> {
        *self.log.lock().unwrap() = lines.iter().map(|l| (*l).to_owned()).collect();
        Arc::clone(self)
    }

    /// Make spawning itself fail, the way a corrupt JRE would.
    pub fn fail_to_spawn(self: &Arc<Self>, detail: &str) -> Arc<Self> {
        *self.fail_with.lock().unwrap() = Some(detail.to_owned());
        Arc::clone(self)
    }
}

impl ProcessPort for FakeProcessPort {
    fn spawn(&self, invocation: &Invocation) -> Result<Box<dyn GameProcess>, AshError> {
        if let Some(detail) = self.fail_with.lock().unwrap().clone() {
            return Err(AshError::LaunchFailed { detail });
        }
        self.spawned.lock().unwrap().push(invocation.clone());
        Ok(Box::new(FakeGameProcess {
            status: Mutex::new(self.outcome.lock().unwrap().unwrap_or(GameStatus::Running)),
            log: self.log.lock().unwrap().clone(),
        }))
    }
}

struct FakeGameProcess {
    status: Mutex<GameStatus>,
    log: Vec<String>,
}

impl GameProcess for FakeGameProcess {
    fn status(&self) -> GameStatus {
        *self.status.lock().unwrap()
    }

    fn log(&self) -> Vec<String> {
        self.log.clone()
    }

    fn stop(&self) {
        *self.status.lock().unwrap() = GameStatus::Exited { code: None, clean: false };
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_view_blanks_secrets_and_keeps_everything_else() {
        let invocation = Invocation {
            program: PathBuf::from("java"),
            args: vec!["--accessToken".into(), "secret-token".into(), "--uuid".into()],
            working_directory: PathBuf::from("."),
            secrets: vec!["secret-token".into()],
        };

        let view = invocation.view();

        assert_eq!(view.args, ["--accessToken", "<redacted>", "--uuid"]);
    }

    #[test]
    fn an_empty_secret_never_blanks_a_real_argument() {
        // A version with no access token to hide must not turn every empty
        // argument into `<redacted>`.
        let invocation = Invocation {
            program: PathBuf::from("java"),
            args: vec!["--xuid".into(), String::new()],
            working_directory: PathBuf::from("."),
            secrets: vec![],
        };

        assert_eq!(invocation.view().args, ["--xuid", ""]);
    }
}
