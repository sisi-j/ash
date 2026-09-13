//! ash's own log.
//!
//! What goes in it is chosen at the call site, and the call sites are only
//! ever handed shapes that carry no credential. The JVM invocation is
//! written from [`crate::InvocationView`], which has no serialiser for the
//! access token and no field holding one; a sign-in is recorded by username;
//! `Session` has a hand-written `Debug` that prints `<redacted>`. Nothing
//! here scrubs a line after the fact, because a scrubber is a thing that can
//! be out of date with what it is scrubbing.
//!
//! There is a tripwire below all the same, and it only trips in debug
//! builds. It is not the mechanism - it is how a mistake in the mechanism
//! gets found by a test rather than by a player pasting a log into Discord.

use std::fmt::Write as _;
use std::fs;
use std::io::Write as _;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

/// Roll over at this size and keep one previous file. A launcher that has
/// run for a year should not hand someone a gigabyte to read.
const MAX_BYTES: u64 = 2 * 1024 * 1024;

pub struct Diagnostics {
    path: PathBuf,
    /// Serialises writes so two threads cannot interleave halves of a line.
    writing: Mutex<()>,
}

impl Diagnostics {
    pub(crate) fn new(data_root: &Path) -> Self {
        Self { path: data_root.join("logs").join("ash.log"), writing: Mutex::new(()) }
    }

    /// Where the log is, so the UI can offer to open it.
    pub fn path(&self) -> &Path {
        &self.path
    }

    pub(crate) fn info(&self, event: &str, detail: &str) {
        self.write("INFO", event, detail);
    }

    pub(crate) fn warn(&self, event: &str, detail: &str) {
        self.write("WARN", event, detail);
    }

    fn write(&self, level: &str, event: &str, detail: &str) {
        let line = format!("{}  {level:<5} {event}  {detail}\n", timestamp(now_ms()));
        debug_assert!(
            !looks_like_a_credential(&line),
            "a credential reached the log: {event}. Log a redacted shape, not a raw one."
        );

        let _guard = self.writing.lock().unwrap();
        // A log that cannot be written is not worth failing a launch over.
        // Every one of these calls is on a path whose real job is something
        // else.
        let _ = self.append(&line);
    }

    fn append(&self, line: &str) -> std::io::Result<()> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }
        if fs::metadata(&self.path).map(|m| m.len()).unwrap_or(0) >= MAX_BYTES {
            // One previous file. The interesting part of a log is almost
            // always the end of it.
            let _ = fs::rename(&self.path, self.path.with_extension("log.1"));
        }
        let mut file = fs::OpenOptions::new().create(true).append(true).open(&self.path)?;
        file.write_all(line.as_bytes())
    }
}

/// Render an invocation for the log.
///
/// Takes the view rather than the [`crate::Invocation`] on purpose: the
/// unredacted shape has no `Serialize` and no `Display`, so the only way to
/// log one would be to write the redaction out by hand here.
pub(crate) fn describe(invocation: &crate::InvocationView) -> String {
    let mut out = String::new();
    let _ = write!(out, "program={}", invocation.program);
    let _ = write!(out, " cwd={}", invocation.working_directory);
    let _ = write!(out, " args={}", invocation.args.len());
    for arg in &invocation.args {
        // One per line, because a 1.21 classpath is sixty paths long and a
        // single line of it is unreadable in any editor.
        let _ = write!(out, "\n    {arg}");
    }
    out
}

/// The tripwire. A JWT header is always `eyJ`, and every credential in this
/// chain is a JWT.
fn looks_like_a_credential(line: &str) -> bool {
    let mut rest = line;
    while let Some(at) = rest.find("eyJ") {
        let boundary = rest[..at].chars().next_back().is_none_or(|c| !c.is_alphanumeric());
        let tail = &rest[at..];
        let end = tail
            .find(|c: char| !(c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.')))
            .unwrap_or(tail.len());
        if boundary && end >= 40 {
            return true;
        }
        rest = &rest[at + 3..];
    }
    false
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or_default()
}

/// `2026-09-12T20:48:45Z`.
///
/// Hand-rolled rather than pulling in a date library for one format string.
/// UTC only: a log read across a timezone change is worse than one that
/// never claimed to be local.
fn timestamp(ms: u64) -> String {
    let seconds = ms / 1000;
    let (days, rest) = (seconds / 86_400, seconds % 86_400);
    let (year, month, day) = civil_from_days(days as i64);
    let (hour, minute, second) = (rest / 3600, (rest % 3600) / 60, rest % 60);
    format!("{year:04}-{month:02}-{day:02}T{hour:02}:{minute:02}:{second:02}Z")
}

/// Howard Hinnant's `civil_from_days`, the standard shift-the-epoch-to-March
/// trick that makes the leap day the last day of the year.
fn civil_from_days(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let day_of_era = (z - era * 146_097) as u64;
    let year_of_era =
        (day_of_era - day_of_era / 1460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let year = year_of_era as i64 + era * 400;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let mp = (5 * day_of_year + 2) / 153;
    let day = (day_of_year - (153 * mp + 2) / 5 + 1) as u32;
    let month = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    (if month <= 2 { year + 1 } else { year }, month, day)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timestamps_are_iso_8601_utc() {
        assert_eq!(timestamp(0), "1970-01-01T00:00:00Z");
        // 2026-09-12T20:48:45Z, checked against an independent converter.
        assert_eq!(timestamp(1_789_246_125_000), "2026-09-12T20:48:45Z");
        // A leap day, which is the case the calendar arithmetic exists for.
        assert_eq!(timestamp(1_709_164_800_000), "2024-02-29T00:00:00Z");
        assert_eq!(timestamp(1_735_689_599_000), "2024-12-31T23:59:59Z");
    }

    #[test]
    fn the_tripwire_knows_a_token_from_a_class_name() {
        let token = "eyJraWQiOiIwNDkxODEiLCJhbGciOiJSUzI1NiJ9.eyJ4dWlkIjoiMjUzNTQwNzcwOCJ9.2xvQ";
        assert!(looks_like_a_credential(&format!("--accessToken {token}")));

        // Java identifiers are dotted and long, which is why length alone
        // cannot be the test.
        assert!(!looks_like_a_credential(
            "at net.minecraft.client.renderer.EntityRenderer.updateCameraAndRender(E.java:1)"
        ));
        assert!(!looks_like_a_credential("loading keyJoin and eyJshort"));
        assert!(!looks_like_a_credential("program=C:/ash/java.exe args=42"));
    }

    #[test]
    fn a_log_rolls_over_rather_than_growing_without_end() {
        let tmp = tempfile::tempdir().expect("temp dir");
        let log = Diagnostics::new(tmp.path());

        let filler = "x".repeat(4096);
        for _ in 0..600 {
            log.info("filler", &filler);
        }

        assert!(log.path().is_file());
        assert!(log.path().metadata().expect("metadata").len() < MAX_BYTES);
        assert!(log.path().with_extension("log.1").is_file(), "the previous log was thrown away");
    }

    #[test]
    fn lines_carry_a_timestamp_a_level_and_an_event() {
        let tmp = tempfile::tempdir().expect("temp dir");
        let log = Diagnostics::new(tmp.path());

        log.info("launch", "instance=modern");
        log.warn("prepare", "detail");

        let written = fs::read_to_string(log.path()).expect("read");
        let lines: Vec<&str> = written.lines().collect();
        assert_eq!(lines.len(), 2);
        assert!(lines[0].starts_with("20"), "no timestamp: {}", lines[0]);
        assert!(lines[0].contains("INFO "));
        assert!(lines[0].contains("launch"));
        assert!(lines[1].contains("WARN "));
    }
}
