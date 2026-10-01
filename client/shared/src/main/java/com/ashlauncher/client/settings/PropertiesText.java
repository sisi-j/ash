package com.ashlauncher.client.settings;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * Changing one value in the text of a properties file, and nothing else.
 *
 * <p>{@link Properties} can read the file and can write one, but writing
 * means writing all of it: comments gone, order lost, a date stamp at the
 * top. A file a player has edited by hand has to come back as they left it,
 * so this finds the one line a setting is on and replaces its value there.
 *
 * <p>"The line a setting is on" is decided by the format's own rules, not by
 * a search for the key: a comment that mentions a setting is not that
 * setting, a value can be continued across lines with a trailing backslash,
 * and a key can be followed by {@code =}, {@code :} or only whitespace. Which
 * key a line holds is asked of {@link Properties} itself, so an escape in a
 * key is read exactly as the game will read it.
 */
final class PropertiesText {

    private PropertiesText() {}

    /**
     * {@code text} with {@code key}'s value replaced by {@code value}, or
     * {@code null} when no line holds {@code key}.
     *
     * <p>When a key is on more than one line, {@link Properties} keeps the
     * last, so the last is the one changed. Everything before the value -
     * indentation, the key as the player spelled it, the separator - is kept,
     * and so is the line ending.
     *
     * @param value written as given; it must need no escaping, which
     *     {@code true} and {@code false} do not
     */
    static String withValue(String text, String key, String value) {
        int replaceFrom = -1;
        int replaceTo = -1;
        boolean wholeLine = false;

        int position = 0;
        while (position < text.length()) {
            int start = skipWhitespace(text, position, text.length());
            if (start == text.length()) {
                break;
            }
            char first = text.charAt(start);
            if (first == '\r' || first == '\n') {
                position = afterTerminator(text, start);
                continue;
            }
            if (first == '#' || first == '!') {
                // A comment is never continued, whatever it ends with.
                position = afterTerminator(text, endOfNaturalLine(text, start));
                continue;
            }

            int end = endOfLogicalLine(text, start);
            if (key.equals(keyOf(text.substring(start, end)))) {
                int keyEnd = keyEnd(text, start, end);
                int valueStart = valueStart(text, keyEnd, end);
                // A key continued onto another line is too odd a thing to edit
                // around: the whole setting is rewritten, still on its own line.
                wholeLine = containsTerminator(text, start, valueStart);
                replaceFrom = wholeLine ? start : valueStart;
                replaceTo = end;
            }
            position = afterTerminator(text, end);
        }

        if (replaceFrom < 0) {
            return null;
        }
        String replacement = wholeLine ? key + "=" + value : value;
        return text.substring(0, replaceFrom) + replacement + text.substring(replaceTo);
    }

    /** The key a single logical line holds, read by {@link Properties} itself. */
    private static String keyOf(String logicalLine) {
        Properties line = new Properties();
        try {
            line.load(new StringReader(logicalLine));
        } catch (IOException | IllegalArgumentException unreadable) {
            return null;
        }
        return line.isEmpty() ? null : (String) line.keys().nextElement();
    }

    /** Where the key ends: at the first unescaped {@code =}, {@code :} or whitespace. */
    private static int keyEnd(String text, int start, int end) {
        int i = start;
        while (i < end) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '=' || c == ':' || isWhitespace(c)) {
                return i;
            }
            i++;
        }
        return end;
    }

    /** Past whitespace, at most one {@code =} or {@code :}, and whitespace again. */
    private static int valueStart(String text, int keyEnd, int end) {
        int i = skipWhitespace(text, keyEnd, end);
        if (i < end && (text.charAt(i) == '=' || text.charAt(i) == ':')) {
            i++;
        }
        return skipWhitespace(text, i, end);
    }

    /**
     * The end of the last natural line of the logical line starting at
     * {@code start}: a line ending in an odd number of backslashes continues
     * onto the next.
     */
    private static int endOfLogicalLine(String text, int start) {
        int end = endOfNaturalLine(text, start);
        while (end < text.length() && endsInContinuation(text, start, end)) {
            end = endOfNaturalLine(text, afterTerminator(text, end));
        }
        return end;
    }

    private static boolean endsInContinuation(String text, int lineStart, int end) {
        int backslashes = 0;
        for (int i = end - 1; i >= lineStart && text.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static int endOfNaturalLine(String text, int from) {
        int i = from;
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
            i++;
        }
        return i;
    }

    /** Past one line ending - {@code \n}, {@code \r} or {@code \r\n}, as Properties allows. */
    private static int afterTerminator(String text, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        if (text.charAt(end) == '\r' && end + 1 < text.length() && text.charAt(end + 1) == '\n') {
            return end + 2;
        }
        return end + 1;
    }

    private static int skipWhitespace(String text, int from, int end) {
        int i = from;
        while (i < end && isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean containsTerminator(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') {
                return true;
            }
        }
        return false;
    }

    /** What Properties counts as whitespace between a key and its value. */
    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }
}
