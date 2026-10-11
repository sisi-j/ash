//! The client's own settings file, `config/ash.properties` in a game
//! directory (CONTEXT.md, "Client settings").
//!
//! The client is its writer while the game runs. The launcher is the only
//! other one, for synced settings, and only while that instance's game is
//! closed (ADR-0022). Its edit is the client's own: the value on the key's
//! line changes, and not one other byte - comments, order, spelling,
//! separators, line endings and unknown keys all stay. A key the file lacks
//! is appended, as the client itself appends settings an older file lacks.
//!
//! Java writes `.properties` as ISO-8859-1, so the file is read and written
//! as bytes mapped one-to-one to characters: whatever ash doesn't touch
//! comes back exactly as it was, whatever its encoding.

use std::path::{Path, PathBuf};

use crate::error::AshError;

/// Where the client keeps its settings, relative to the game directory.
pub(crate) const CLIENT_SETTINGS: &str = "config/ash.properties";

pub(crate) fn path(game_directory: &Path) -> PathBuf {
    game_directory.join(CLIENT_SETTINGS)
}

fn latin1(bytes: &[u8]) -> String {
    bytes.iter().map(|&b| b as char).collect()
}

fn to_latin1(text: &str) -> Option<Vec<u8>> {
    text.chars().map(|c| u8::try_from(c as u32).ok()).collect()
}

/// The file's text, or `None` when it isn't there.
pub(crate) fn read_text(game_directory: &Path) -> Option<String> {
    std::fs::read(path(game_directory)).ok().map(|b| latin1(&b))
}

/// The keys and values of a Java properties file, as far as ash's own
/// settings need: comments, `=`, `:` or whitespace between a key and its
/// value, and continued lines skipped rather than misread as keys. The keys
/// ash reads have no escapes in them, so escapes are left as they are.
pub(crate) fn properties(raw: &str) -> Vec<(String, String)> {
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

/// Whether ash may write `value` as it is: no escaping needed, which every
/// value the client formats satisfies. Anything else is never written.
pub(crate) fn writable(key: &str, value: &str) -> bool {
    let plain = |s: &str| !s.chars().any(|c| matches!(c, '\\' | '\n' | '\r') || (c as u32) > 0xFF);
    plain(key)
        && plain(value)
        && !key.is_empty()
        && !key.chars().any(|c| c == '=' || c == ':' || c.is_whitespace() || c == '#' || c == '!')
        && !value.starts_with(char::is_whitespace)
}

/// `text` with `key`'s value replaced by `value` - the last line holding it,
/// as `Properties` keeps the last - or with `key=value` appended.
///
/// Everything before the value on that line stays as written. A key alone
/// on its line gets an `=` of its own, or the value would join the key.
pub(crate) fn with_value(text: &str, key: &str, value: &str) -> String {
    let mut found: Option<(usize, usize, bool)> = None;
    let mut offset = 0;
    let mut continued = false;
    for line in text.split_inclusive('\n') {
        let start = offset;
        offset += line.len();
        let was_continued = continued;
        let content = line.trim_end_matches(['\n', '\r']);
        continued = content.chars().rev().take_while(|&c| c == '\\').count() % 2 == 1;
        let indent = content.len() - content.trim_start().len();
        let body = &content[indent..];
        if was_continued
            || continued
            || body.is_empty()
            || body.starts_with('#')
            || body.starts_with('!')
        {
            continue;
        }
        let key_end =
            body.find(|c: char| c == '=' || c == ':' || c.is_whitespace()).unwrap_or(body.len());
        if &body[..key_end] != key {
            continue;
        }
        let after_key = &body[key_end..];
        let spaced = after_key.len() - after_key.trim_start().len();
        let mut value_at = key_end + spaced;
        if body[value_at..].starts_with(['=', ':']) {
            value_at += 1;
            let rest = &body[value_at..];
            value_at += rest.len() - rest.trim_start().len();
        }
        let bare = value_at == key_end;
        let from = start + indent + value_at;
        let to = start + content.len();
        found = Some((from, to, bare));
    }
    match found {
        Some((from, to, bare)) => {
            let replacement = if bare { format!("={value}") } else { value.to_owned() };
            format!("{}{}{}", &text[..from], replacement, &text[to..])
        }
        None => {
            let newline = if text.contains("\r\n") { "\r\n" } else { "\n" };
            let separator = if text.is_empty() || text.ends_with('\n') { "" } else { newline };
            format!("{text}{separator}{key}={value}{newline}")
        }
    }
}

/// Set each of `values` in a game directory's settings file, creating it if
/// the client hasn't run there yet. Writes only if something changed, and
/// skips any value that would need escaping. Returns whether it wrote.
pub(crate) fn set_values(
    game_directory: &Path,
    values: &[(String, String)],
) -> Result<bool, AshError> {
    let before = read_text(game_directory).unwrap_or_default();
    let mut text = before.clone();
    for (key, value) in values {
        if !writable(key, value) {
            continue;
        }
        let current = properties(&text).into_iter().rev().find(|(k, _)| k == key).map(|(_, v)| v);
        if current.as_deref() != Some(value.as_str()) {
            text = with_value(&text, key, value);
        }
    }
    if text == before {
        return Ok(false);
    }
    let bytes = to_latin1(&text).ok_or_else(|| AshError::Storage {
        detail: "the client settings would not be ISO-8859-1".into(),
    })?;
    let file = path(game_directory);
    if let Some(parent) = file.parent() {
        std::fs::create_dir_all(parent).map_err(AshError::writing("creating the config folder"))?;
    }
    let temporary = file.with_extension("properties.ash-tmp");
    std::fs::write(&temporary, bytes).map_err(AshError::writing("writing the client settings"))?;
    std::fs::rename(&temporary, &file).map_err(AshError::writing("writing the client settings"))?;
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_the_value_changes_and_every_other_byte_stays() {
        let text =
            "# The crosshair\r\ncrosshair.size=4\r\n  crosshair.gap : 1\r\nunknown.key=keep me\r\n";
        assert_eq!(
            with_value(text, "crosshair.gap", "3"),
            "# The crosshair\r\ncrosshair.size=4\r\n  crosshair.gap : 3\r\nunknown.key=keep me\r\n"
        );
    }

    #[test]
    fn the_last_line_holding_a_key_is_the_one_changed_as_properties_keeps_the_last() {
        assert_eq!(with_value("a=1\na=2\n", "a", "9"), "a=1\na=9\n");
    }

    #[test]
    fn a_key_alone_on_its_line_gets_a_separator() {
        assert_eq!(with_value("a\nb=1\n", "a", "x"), "a=x\nb=1\n");
    }

    #[test]
    fn a_missing_key_is_appended_in_the_file_s_own_line_ending() {
        assert_eq!(with_value("a=1\r\n", "b", "2"), "a=1\r\nb=2\r\n");
        assert_eq!(with_value("a=1", "b", "2"), "a=1\nb=2\n");
        assert_eq!(with_value("", "b", "2"), "b=2\n");
    }

    #[test]
    fn comments_and_continued_lines_are_never_mistaken_for_the_key() {
        let text = "# a=1\nother=x\\\na=2\na=3\n";
        assert_eq!(with_value(text, "a", "9"), "# a=1\nother=x\\\na=2\na=9\n");
    }

    #[test]
    fn a_key_that_merely_starts_with_another_is_not_it() {
        assert_eq!(
            with_value("crosshair.size.extra=1\n", "crosshair.size", "5"),
            "crosshair.size.extra=1\ncrosshair.size=5\n"
        );
    }

    #[test]
    fn values_needing_escapes_are_not_writable() {
        assert!(writable("crosshair.colour", "#FFFFFFFF"));
        assert!(writable("fps-readout.position", "top-left 4 4"));
        assert!(!writable("a", "x\\y"));
        assert!(!writable("a", "x\ny"));
        assert!(!writable("a", " leading"));
        assert!(!writable("a b", "x"));
        assert!(!writable("a", "\u{263a}"));
    }

    #[test]
    fn set_values_writes_only_what_differs_and_keeps_latin1_bytes() {
        let dir = tempfile::tempdir().unwrap();
        let file = path(dir.path());
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        // 0xE9 is 'é' in ISO-8859-1, as Java would have written it.
        std::fs::write(&file, b"# caf\xe9\ncrosshair.size=4\n").unwrap();

        assert!(!set_values(dir.path(), &[("crosshair.size".into(), "4".into())]).unwrap());
        assert!(set_values(dir.path(), &[("crosshair.size".into(), "6".into())]).unwrap());

        assert_eq!(std::fs::read(&file).unwrap(), b"# caf\xe9\ncrosshair.size=6\n");
    }

    #[test]
    fn set_values_creates_the_file_where_the_client_has_not_run() {
        let dir = tempfile::tempdir().unwrap();
        assert!(set_values(dir.path(), &[("crosshair.size".into(), "6".into())]).unwrap());
        assert_eq!(std::fs::read_to_string(path(dir.path())).unwrap(), "crosshair.size=6\n");
    }
}
