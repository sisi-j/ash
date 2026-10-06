package com.ashlauncher.client.freelook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The servers whose published rules ban freelook, on which ash switches it
 * off. See ADR-0006's 2026-09-30 amendment.
 *
 * <p>Short and sourced on purpose: each entry cites the page that bans it,
 * so that it can be kept true. Missing a server is the accepted risk, and an
 * unlisted server's ban is a reason to add it here, not to argue.
 *
 * <p>Matched by the address the player connected with: a listed domain or
 * any of its subdomains. Each listed network keeps every address a player is
 * likely to type, and every SRV target, inside one domain
 * ({@code docs/research/0009}, section 3). The server's own name for itself is
 * not matched yet, because what each one sends has not been seen (section 2).
 */
public final class BlockList {

    /** A server that bans freelook, and where it says so. */
    public static final class Server {
        private final String name;
        private final List<String> domains;
        private final String rules;

        Server(String name, String rules, String... domains) {
            this.name = name;
            this.rules = rules;
            this.domains = Collections.unmodifiableList(Arrays.asList(domains));
        }

        /** What the player calls it. */
        public String name() {
            return name;
        }

        /** The page whose rules ban freelook there. */
        public String rules() {
            return rules;
        }

        public List<String> domains() {
            return domains;
        }

        /** What the key says on this server, when it is pressed. */
        public String whyOff() {
            return "Freelook is off on " + name + ": its rules ban it.";
        }
    }

    /** Every listed server, with the rules page that bans freelook there. */
    public static final List<Server> SERVERS = Collections.unmodifiableList(Arrays.asList(
            // "must not ... change the player's perspective (e.g. allowing them
            // to see around or over objects they normally wouldn't be able to)"
            new Server("Hypixel",
                    "https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications",
                    "hypixel.net"),
            // Disallowed: "Better F5 (Allowing you to manipulate your camera
            // whilst your movement remains static)"
            new Server("MCC Island", "https://mcchampionship.com/help/mods/", "mccisland.net"),
            // "Camera perspective mods that exceed the capabilities of the F5
            // camera function are prohibited. Freelook / Freecam"
            new Server("Hoplite", "https://www.hoplite.gg/rules", "hoplite.gg")));

    private BlockList() {
    }

    /**
     * The listed server this address belongs to, or null.
     *
     * @param address as the player wrote it: {@code mc.hypixel.net},
     *     {@code Hypixel.net:25565}, {@code [::1]:25565}; null or empty for
     *     singleplayer
     */
    public static Server match(String address) {
        String host = host(address);
        if (host.isEmpty()) {
            return null;
        }
        for (Server server : SERVERS) {
            for (String domain : server.domains) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return server;
                }
            }
        }
        return null;
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
