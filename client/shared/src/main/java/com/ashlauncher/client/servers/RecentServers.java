package com.ashlauncher.client.servers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every server the player joins, with when, for the launcher's servers card to
 * put the most recent first. The game's own list records no recency
 * ({@code docs/research/0008}), so ash's client keeps this, however the player
 * joined: the list, direct connect, or the launcher's own Join.
 *
 * <p>{@code ash/recent-servers.json} in the game directory, written by the
 * client and only read by the launcher, like the load report. It holds
 * addresses and times and nothing else.
 */
public final class RecentServers {

    /** Where the client writes it, relative to the game directory. */
    public static final String RELATIVE_PATH = "ash/recent-servers.json";

    /** As many as the card could ever want; older joins drop off the end. */
    static final int KEPT = 20;

    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*\"address\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"joined_ms\"\\s*:\\s*(\\d+)\\s*}");

    /** One join: the address as the player wrote it, and when. */
    public static final class Join {
        public final String address;
        public final long joinedMs;

        Join(String address, long joinedMs) {
            this.address = address;
            this.joinedMs = joinedMs;
        }
    }

    private RecentServers() {
    }

    /**
     * Records a join, most recent first, replacing an earlier join to the same
     * address. Through a temporary file and a move, so the launcher never
     * reads half a file.
     *
     * @throws IOException for the caller to log; never worth stopping the game over
     */
    public static void record(Path gameDir, String address, long nowMs) throws IOException {
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Path file = gameDir.resolve(RELATIVE_PATH);
        List<Join> joins = new ArrayList<>();
        joins.add(new Join(trimmed, nowMs));
        for (Join earlier : read(file)) {
            if (joins.size() < KEPT && !same(earlier.address, trimmed)) {
                joins.add(earlier);
            }
        }
        Files.createDirectories(file.getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        Files.write(partial, toJson(joins).getBytes(StandardCharsets.UTF_8));
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The joins on record, most recent first; none if there is no file or it cannot be read. */
    public static List<Join> read(Path file) {
        List<Join> joins = new ArrayList<>();
        String json;
        try {
            json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            return joins;
        }
        Matcher entry = ENTRY.matcher(json);
        while (entry.find()) {
            try {
                joins.add(new Join(unquoted(entry.group(1)), Long.parseLong(entry.group(2))));
            } catch (NumberFormatException tooLong) {
                // Not a time this client wrote; the rest still stands.
            }
        }
        return joins;
    }

    /** One server however it was written: case, spaces and a trailing dot aside. */
    static boolean same(String a, String b) {
        return normal(a).equals(normal(b));
    }

    private static String normal(String address) {
        String host = address.trim().toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    static String toJson(List<Join> joins) {
        StringBuilder json = new StringBuilder("{\n  \"servers\": [");
        for (int i = 0; i < joins.size(); i++) {
            json.append(i == 0 ? "\n" : ",\n");
            json.append("    { \"address\": ").append(quoted(joins.get(i).address))
                    .append(", \"joined_ms\": ").append(joins.get(i).joinedMs).append(" }");
        }
        json.append(joins.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return json.toString();
    }

    private static String quoted(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String unquoted(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'u' && i + 4 < value.length()) {
                    out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                    i += 4;
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
