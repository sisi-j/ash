//! "Copy report" (spec 0005): what a tester pastes into feedback when
//! something goes wrong. Plain text, shown before it is copied, and never sent
//! anywhere by ash.
//!
//! ash's own log already has its tokens redacted. A Minecraft access token is
//! a JWT, so anything JWT-shaped is blanked again here, in ash's log and the
//! game's output alike, in case something ever printed one.

use std::path::Path;

/// How many lines of ash's log, and of the game's output, a report carries.
pub const REPORT_LINES: usize = 200;

/// What a report is about, beyond ash and the computer.
#[derive(Debug, Default)]
pub struct ReportSubject {
    /// "1.21.11, Fabric, your mods on".
    pub instance: Option<String>,
    pub error_kind: Option<String>,
    pub error_message: Option<String>,
    /// The game's output, for a crash.
    pub game_output: Option<Vec<String>>,
}

pub(crate) fn build(
    ash_version: &str,
    windows: &str,
    log: &Path,
    subject: &ReportSubject,
) -> String {
    let mut out = String::new();
    out.push_str("ash report\n");
    out.push_str(&format!("ash: {ash_version}\n"));
    out.push_str(&format!("Windows: {windows}\n"));
    if let Some(instance) = &subject.instance {
        out.push_str(&format!("Instance: {instance}\n"));
    }
    if subject.error_kind.is_some() || subject.error_message.is_some() {
        let kind = subject.error_kind.as_deref().unwrap_or("error");
        let message = subject.error_message.as_deref().unwrap_or("");
        out.push_str(&format!("Error: {kind}: {message}\n"));
    }
    if let Some(output) = &subject.game_output {
        out.push_str(&format!("\n--- the game's last {} lines ---\n", REPORT_LINES));
        for line in tail(output.iter().map(String::as_str), REPORT_LINES) {
            out.push_str(&redact(line));
            out.push('\n');
        }
    }
    out.push_str(&format!("\n--- ash's last {} log lines ---\n", REPORT_LINES));
    let text = std::fs::read_to_string(log).unwrap_or_default();
    for line in tail(text.lines(), REPORT_LINES) {
        out.push_str(&redact(line));
        out.push('\n');
    }
    out
}

fn tail<'a>(lines: impl Iterator<Item = &'a str>, n: usize) -> Vec<&'a str> {
    let all: Vec<&str> = lines.collect();
    all[all.len().saturating_sub(n)..].to_vec()
}

/// Anything shaped like a JWT - three base64url parts, the first starting
/// `eyJ` - becomes `<redacted>`.
pub(crate) fn redact(line: &str) -> String {
    let mut out = String::with_capacity(line.len());
    let mut rest = line;
    while let Some(at) = rest.find("eyJ") {
        out.push_str(&rest[..at]);
        let candidate = &rest[at..];
        let end = candidate
            .find(|c: char| !(c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.'))
            .unwrap_or(candidate.len());
        let token = &candidate[..end];
        if token.split('.').filter(|part| !part.is_empty()).count() >= 3 {
            out.push_str("<redacted>");
        } else {
            out.push_str(token);
        }
        rest = &candidate[end..];
    }
    out.push_str(rest);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const JWT: &str = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdGV2ZSJ9.c2lnbmF0dXJl";

    #[test]
    fn a_token_shaped_thing_is_blanked_and_ordinary_text_is_not() {
        assert_eq!(redact(&format!("token={JWT} end")), "token=<redacted> end");
        assert_eq!(redact("eyJust a word, and eyJ.no"), "eyJust a word, and eyJ.no");
        assert_eq!(
            redact("[Render thread/INFO]: Loaded 1371 recipes"),
            "[Render thread/INFO]: Loaded 1371 recipes"
        );
    }

    #[test]
    fn a_report_says_what_and_where_and_keeps_only_the_tail() {
        let tmp = tempfile::tempdir().unwrap();
        let log = tmp.path().join("ash.log");
        let lines: Vec<String> = (0..300).map(|i| format!("log line {i}")).collect();
        std::fs::write(&log, lines.join("\n")).unwrap();
        let subject = ReportSubject {
            instance: Some("1.21.11, Fabric, your mods on".into()),
            error_kind: Some("java_failed".into()),
            error_message: Some("Java could not start the game.".into()),
            game_output: Some(vec![format!("Setting user: Steve {JWT}")]),
        };

        let report = build("0.1.0-alpha.1", "Windows 11 Pro 24H2 (build 26200)", &log, &subject);

        assert!(report.contains("ash: 0.1.0-alpha.1"));
        assert!(report.contains("Windows: Windows 11 Pro 24H2 (build 26200)"));
        assert!(report.contains("Instance: 1.21.11, Fabric, your mods on"));
        assert!(report.contains("Error: java_failed: Java could not start the game."));
        assert!(report.contains("Setting user: Steve <redacted>"));
        assert!(!report.contains(JWT));
        assert!(report.contains("log line 299") && report.contains("log line 100"));
        assert!(!report.contains("log line 99\n"), "only the last 200 lines");
    }
}
