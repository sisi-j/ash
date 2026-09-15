//! Making the game's output readable.
//!
//! Mojang's log4j configuration - the same file that carries the Log4Shell
//! mitigation for old versions - sets the console appender to an XML layout.
//! Applying it is not optional, so the game's stdout arrives as a stream of
//! `<log4j:Event>` elements rather than lines a person can read.
//!
//! This turns them back. Anything that is not one of those elements passes
//! through untouched, which is what keeps a JVM error - printed before log4j
//! exists, and exactly the output that matters when a launch fails - intact.

/// Render captured output as readable lines, with credentials removed.
pub(crate) fn readable(raw: &[String]) -> Vec<String> {
    let joined = raw.join("\n");
    let joined = redact(&joined);
    let mut out = Vec::new();
    let mut rest = joined.as_str();

    while let Some(open) = find_open(rest) {
        plain(&mut out, &rest[..open]);
        let event = &rest[open..];

        let Some((close_at, close_len)) = find_close(event) else {
            // A half-written final event, which is what a crash mid-line
            // looks like. Keep whatever text it holds.
            plain(&mut out, event);
            return out;
        };

        out.extend(render(&event[..close_at]));
        rest = &event[close_at + close_len..];
    }

    plain(&mut out, rest);
    out
}

/// Strip bearer tokens out of anything the game printed.
///
/// 1.8.9 logs its whole session on startup - `(Session ID is token:<jwt>:
/// <uuid>)` - so ash takes the one credential it kept off the command line
/// and out of every file, and then hands it to the UI in a crash report.
///
/// Matched by shape rather than by comparing against the token ash issued,
/// because ash does not keep that token and should not start: a mod, a
/// server, or a future version printing some *other* credential is the same
/// problem, and holding the secret in memory to find it would be trading one
/// exposure for another.
fn redact(text: &str) -> String {
    // Every token in this chain is a JWT, and a JWT header always begins
    // `eyJ` - base64url for `{"`. Java class and method names are dotted
    // too, which is why length alone cannot be the test.
    const MARKER: &str = "eyJ";

    let mut out = String::with_capacity(text.len());
    let mut rest = text;

    while let Some(at) = rest.find(MARKER) {
        // Only at a boundary, so `keyJoin` is not mistaken for a token.
        let boundary = rest[..at].chars().next_back().is_none_or(|c| !c.is_alphanumeric());
        let end = at + token_length(&rest[at..]);

        if boundary && end - at >= 40 {
            out.push_str(&rest[..at]);
            out.push_str("<redacted>");
            rest = &rest[end..];
        } else {
            out.push_str(&rest[..at + MARKER.len()]);
            rest = &rest[at + MARKER.len()..];
        }
    }

    out.push_str(rest);
    out
}

/// How much of this text is base64url and dots.
fn token_length(text: &str) -> usize {
    text.find(|c: char| !(c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.')))
        .unwrap_or(text.len())
}

fn find_open(text: &str) -> Option<usize> {
    // `LegacyXMLLayout` (modern versions) and `XMLLayout` (old ones) differ
    // only in whether the tag is namespaced.
    match (text.find("<log4j:Event"), text.find("<Event ")) {
        (Some(a), Some(b)) => Some(a.min(b)),
        (Some(a), None) => Some(a),
        (None, Some(b)) => Some(b),
        (None, None) => None,
    }
}

fn find_close(text: &str) -> Option<(usize, usize)> {
    if let Some(at) = text.find("</log4j:Event>") {
        return Some((at, "</log4j:Event>".len()));
    }
    text.find("</Event>").map(|at| (at, "</Event>".len()))
}

fn render(event: &str) -> Vec<String> {
    let level = attribute(event, "level").unwrap_or("INFO");
    let thread = attribute(event, "thread").unwrap_or("main");

    let mut lines = Vec::new();
    let message = element(event, "Message").unwrap_or_default();
    // Only the first line is labelled. A wrapped message is still one
    // message, and repeating the prefix down a stack trace makes it harder
    // to read, not easier.
    let mut first = true;
    for line in message.lines() {
        lines.push(if first { format!("[{thread}/{level}]: {line}") } else { line.to_owned() });
        first = false;
    }
    if first {
        lines.push(format!("[{thread}/{level}]: "));
    }

    if let Some(throwable) = element(event, "Throwable") {
        lines.extend(throwable.lines().map(str::to_owned));
    }

    lines
}

/// The value of `name="..."` on the opening tag.
fn attribute<'a>(event: &'a str, name: &str) -> Option<&'a str> {
    // The leading space keeps `level` from matching inside another
    // attribute's name or value.
    let needle = format!(" {name}=\"");
    let at = event.find(&needle)? + needle.len();
    let rest = &event[at..];
    let end = rest.find('"')?;
    Some(&rest[..end])
}

/// The text of `<tag>`, with or without the `log4j:` prefix.
fn element<'a>(event: &'a str, tag: &str) -> Option<&'a str> {
    let start = ["<log4j:", "<"].iter().find_map(|prefix| event.find(&format!("{prefix}{tag}")))?;
    let after = &event[start..];
    let body = &after[after.find('>')? + 1..];
    let end = body.find("</")?;
    let text = &body[..end];

    // log4j wraps message text in CDATA; unwrap it when it did.
    let text = text.trim();
    let text = text.strip_prefix("<![CDATA[").unwrap_or(text);
    let text = text.strip_suffix("]]>").unwrap_or(text);
    Some(text)
}

fn plain(out: &mut Vec<String>, text: &str) {
    out.extend(text.lines().map(str::trim_end).filter(|l| !l.is_empty()).map(str::to_owned));
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lines(text: &str) -> Vec<String> {
        text.lines().map(str::to_owned).collect()
    }

    #[test]
    fn an_event_becomes_one_readable_line() {
        let raw = lines(
            r#"<log4j:Event logger="net.minecraft.client.main.Main" timestamp="1757700000000" level="INFO" thread="Render thread">
<log4j:Message><![CDATA[Setting user: oogz]]></log4j:Message>
</log4j:Event>"#,
        );

        assert_eq!(readable(&raw), ["[Render thread/INFO]: Setting user: oogz"]);
    }

    #[test]
    fn a_throwable_keeps_its_stack_trace() {
        let raw = lines(
            r#"<log4j:Event level="ERROR" thread="main">
<log4j:Message><![CDATA[Failed to start]]></log4j:Message>
<log4j:Throwable><![CDATA[java.lang.OutOfMemoryError: Java heap space
	at net.minecraft.Foo.bar(Foo.java:12)]]></log4j:Throwable>
</log4j:Event>"#,
        );

        assert_eq!(
            readable(&raw),
            [
                "[main/ERROR]: Failed to start",
                "java.lang.OutOfMemoryError: Java heap space",
                "\tat net.minecraft.Foo.bar(Foo.java:12)",
            ]
        );
    }

    #[test]
    fn output_that_is_not_an_event_passes_through() {
        // A JVM that fails before log4j exists is exactly the output that
        // matters when a launch goes wrong.
        let raw = lines("Error: A JNI error has occurred\nUnsupported class file major version 65");

        assert_eq!(
            readable(&raw),
            ["Error: A JNI error has occurred", "Unsupported class file major version 65"]
        );
    }

    #[test]
    fn plain_output_around_events_is_kept() {
        let raw = lines(
            r#"Picked up JAVA_TOOL_OPTIONS
<log4j:Event level="WARN" thread="main"><log4j:Message><![CDATA[hmm]]></log4j:Message></log4j:Event>
Exception in thread "main""#,
        );

        assert_eq!(
            readable(&raw),
            ["Picked up JAVA_TOOL_OPTIONS", "[main/WARN]: hmm", "Exception in thread \"main\""]
        );
    }

    #[test]
    fn the_unprefixed_modern_shape_is_read_too() {
        let raw = lines(
            r#"<Event level="INFO" thread="Render thread"><Message><![CDATA[Sound engine started]]></Message></Event>"#,
        );

        assert_eq!(readable(&raw), ["[Render thread/INFO]: Sound engine started"]);
    }

    #[test]
    fn a_truncated_final_event_is_not_dropped() {
        // What the tail of a log looks like when the JVM dies mid-write.
        let raw = lines(
            r#"<log4j:Event level="ERROR" thread="main">
<log4j:Message><![CDATA[the last thing it said"#,
        );

        assert!(
            readable(&raw).iter().any(|l| l.contains("the last thing it said")),
            "the final partial event was thrown away"
        );
    }

    #[test]
    fn a_multi_line_message_is_labelled_once() {
        let raw = lines(
            r#"<log4j:Event level="INFO" thread="main"><log4j:Message><![CDATA[first
second]]></log4j:Message></log4j:Event>"#,
        );

        assert_eq!(readable(&raw), ["[main/INFO]: first", "second"]);
    }

    #[test]
    fn the_session_1_8_9_prints_on_startup_is_redacted() {
        // Verbatim shape, from a real 1.8.9 launch. The token is shortened
        // here; a real one runs to about eight hundred characters.
        let token = "eyJraWQiOiIwNDkxODEiLCJhbGciOiJSUzI1NiJ9.eyJ4dWlkIjoiMjUzNTQwNzcwODUyNzY4NiJ9.2xvQJWJF";
        let raw = lines(&format!(
            "<log4j:Event level=\"INFO\" thread=\"Client thread\"><log4j:Message><![CDATA[(Session ID is token:{token}:99bffcc8ae2549a0a70481c9c3db7ced)]]></log4j:Message></log4j:Event>"
        ));

        let out = readable(&raw);

        assert!(!out.iter().any(|l| l.contains("eyJ")), "the session token survived: {out:?}");
        assert!(out[0].contains("<redacted>"));
        // The rest of the line is still worth reading.
        assert!(out[0].contains("Session ID is token:"));
        assert!(out[0].contains("99bffcc8ae2549a0a70481c9c3db7ced"));
    }

    #[test]
    fn a_stack_trace_is_not_mistaken_for_a_token() {
        // Java identifiers are dotted and long, which is why the test is the
        // `eyJ` header rather than shape and length alone.
        let raw = lines(
            "\tat net.minecraft.client.renderer.EntityRenderer.updateCameraAndRender(EntityRenderer.java:1234)",
        );

        assert_eq!(readable(&raw), raw);
    }

    #[test]
    fn ordinary_words_beginning_with_the_marker_survive() {
        let raw = lines("loading keyJoin and eyJshort from disk");
        assert_eq!(readable(&raw), raw);
    }

    #[test]
    fn nothing_in_produces_nothing_out() {
        assert!(readable(&[]).is_empty());
        assert!(readable(&lines("\n\n")).is_empty());
    }
}
