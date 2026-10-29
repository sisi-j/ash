package com.ashlauncher.client.fairplay;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.report.Feature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The fair-play list: which of ash's features are off on which servers, and
 * why (spec 0007, Fair play). One list for every feature, shipped in the
 * client as {@code assets/ash/fair-play.txt}, whose comments say how a rule
 * is written.
 *
 * <p>A feature asks it in one way, {@link #whyOff}, and has no rules of its
 * own: adding a rule for a feature is a line in the list, not code. Off means
 * the feature does nothing there. The player's own switch is never touched,
 * so the feature is back the moment they leave.
 */
public final class FairPlay {

    /** Where the list is, among the client's resources. */
    static final String RESOURCE = "/assets/ash/fair-play.txt";

    /**
     * A system property naming a file of more rules, in the same format, read
     * afresh each time: the real-game test's own list, which it writes
     * mid-test to put its local server on it. Never set outside a test run,
     * as {@code ash.cosmetics.cdn} isn't.
     */
    public static final String TEST_LIST_PROPERTY = "ash.test.fairPlayList";

    /** One rule: a feature, off on a server's addresses, for a reason its rules give. */
    public static final class Rule {

        private final Feature feature;
        private final String server;
        private final List<String> addresses;
        private final String why;
        private final String source;

        private Rule(Feature feature, String server, List<String> addresses, String why, String source) {
            this.feature = feature;
            this.server = server;
            this.addresses = addresses;
            this.why = why;
            this.source = source;
        }

        public Feature feature() {
            return feature;
        }

        /** What the player calls the server. */
        public String server() {
            return server;
        }

        /** The page whose rules ban the feature there. */
        public String source() {
            return source;
        }

        /** What the player is told, in game and on the feature's tile. */
        public String whyOff() {
            return feature.displayName() + " is off on " + server + ": " + why + ".";
        }

        /** Whether a host, as {@link #host} leaves it, is one of the server's addresses. */
        boolean covers(String host) {
            for (String address : addresses) {
                if (address.startsWith("*.")) {
                    String domain = address.substring(2);
                    if (host.equals(domain) || host.endsWith("." + domain)) {
                        return true;
                    }
                } else if (host.equals(address)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static FairPlay shipped;

    private final List<Rule> rules;

    private FairPlay(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(rules);
    }

    /** The list this client shipped with, read once. */
    public static synchronized FairPlay shipped() {
        if (shipped == null) {
            try (InputStream in = FairPlay.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("no " + RESOURCE + " in the client");
                }
                ByteArrayOutputStream read = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    read.write(buffer, 0, n);
                }
                shipped = parse(new String(read.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException unreadable) {
                throw new IllegalStateException(RESOURCE + " could not be read", unreadable);
            }
        }
        return shipped;
    }

    /**
     * The rules in force: the shipped list's, and in a real-game test, the
     * test's own as its file says just now.
     */
    public static FairPlay inForce() {
        String testList = System.getProperty(TEST_LIST_PROPERTY);
        if (testList == null) {
            return shipped();
        }
        List<Rule> rules = new ArrayList<>(shipped().rules);
        try {
            rules.addAll(parse(new String(Files.readAllBytes(Paths.get(testList)), StandardCharsets.UTF_8)).rules);
        } catch (IOException none) {
            // No file yet: the test hasn't listed its server.
        }
        return new FairPlay(rules);
    }

    /**
     * A list in the shipped list's format. A line that isn't a whole rule for
     * a feature ash has is refused, so a mistake in the list fails its tests
     * rather than quietly letting a feature run where it's banned.
     */
    static FairPlay parse(String text) {
        List<Rule> rules = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\|");
            if (parts.length != 5) {
                throw new IllegalArgumentException("not a rule, which has five parts: " + line);
            }
            Feature feature = feature(parts[0].trim());
            List<String> addresses = new ArrayList<>();
            for (String address : parts[2].split(",")) {
                addresses.add(address.trim().toLowerCase(Locale.ROOT));
            }
            rules.add(new Rule(feature, parts[1].trim(), Collections.unmodifiableList(addresses), parts[3].trim(),
                    parts[4].trim()));
        }
        return new FairPlay(rules);
    }

    private static Feature feature(String id) {
        for (Feature feature : Feature.values()) {
            if (feature.id().equals(id)) {
                return feature;
            }
        }
        throw new IllegalArgumentException("no feature " + id + " among " + Arrays.toString(Feature.values()));
    }

    public List<Rule> rules() {
        return rules;
    }

    /**
     * The rule that switches a feature off at this address, or {@code null}.
     *
     * @param address as the player wrote it: {@code mc.hypixel.net},
     *     {@code Hypixel.net:25565}, {@code [::1]:25565}; null or empty for
     *     singleplayer
     */
    public Rule ruleFor(Feature feature, String address) {
        for (Rule rule : rulesAt(address)) {
            if (rule.feature == feature) {
                return rule;
            }
        }
        return null;
    }

    /** Every rule in force at this address, in the list's order. */
    public List<Rule> rulesAt(String address) {
        String host = host(address);
        List<Rule> found = new ArrayList<>();
        if (host.isEmpty()) {
            return found;
        }
        for (Rule rule : rules) {
            if (rule.covers(host)) {
                found.add(rule);
            }
        }
        return found;
    }

    /** Why a feature is off where the player is, as the game says; empty where it isn't. */
    public String whyOff(Feature feature, GameView game) {
        Rule rule = ruleFor(feature, game.serverAddress());
        return rule == null ? "" : rule.whyOff();
    }

    /** The host part of an address, lower-cased, with no port and no trailing dot. */
    static String host(String address) {
        if (address == null) {
            return "";
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            // An IPv6 literal: never a listed domain.
            int end = host.indexOf(']');
            return end < 0 ? "" : host.substring(1, end);
        }
        int colon = host.indexOf(':');
        if (colon >= 0 && host.indexOf(':', colon + 1) < 0) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }
}
