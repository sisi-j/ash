package com.ashlauncher.client.cosmetics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The capes ash players wear, as their published profiles say (spec 0004,
 * ADR-0023, research 0012).
 *
 * <p>Each player's profile is a static file on ash's CDN,
 * {@code profiles/<uuid>.json}: no ash server code runs for a lookup, and
 * none of them is recorded. A missing file is a player with no ash
 * cosmetics, which is also how a player without ash looks.
 *
 * <p>Asked from the render thread, once a frame per player in view, so it
 * never waits: it answers with what it already has, and fetches in the
 * background what it doesn't, or what has gone stale. Textures are only ever
 * taken from ash's own CDN, whatever a profile says.
 */
public final class CapeSource {

    /** A system property that points the client at another CDN: the real-game tests' own. */
    public static final String CDN_PROPERTY = "ash.cosmetics.cdn";
    public static final String DEFAULT_CDN = "https://cdn.ashlauncher.com";

    /** How long a profile is trusted before it is asked again: a changed cape shows within this. */
    static final long FRESH_MS = 120_000;
    static final int PROFILE_MAX_BYTES = 16 * 1024;
    static final int TEXTURE_MAX_BYTES = 256 * 1024;

    private static final Pattern CAPE_URL =
            Pattern.compile("\"cape\"\\s*:\\s*\\{[^{}]*?\"texture_url\"\\s*:\\s*\"([^\"\\\\]+)\"");

    /** One cape: where its texture is, which is also its identity, and its PNG. */
    public static final class Cape {
        private final String url;
        private final byte[] png;

        Cape(String url, byte[] png) {
            this.url = url;
            this.png = png;
        }

        public String url() {
            return url;
        }

        public byte[] png() {
            return png;
        }
    }

    /** Fetches a URL's body, up to {@code max} bytes; null when it isn't there (404). */
    public interface Fetch {
        byte[] get(String url, int max) throws IOException;
    }

    private static final class Known {
        final long at;
        /** The cape's texture address, or null for none. */
        final String capeUrl;

        Known(long at, String capeUrl) {
            this.at = at;
            this.capeUrl = capeUrl;
        }
    }

    private final String cdn;
    private final Fetch fetch;
    private final Executor background;
    private final LongSupplier clock;
    private final Map<UUID, Known> profiles = new ConcurrentHashMap<>();
    private final Map<String, byte[]> textures = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public CapeSource(String cdn, Fetch fetch, Executor background, LongSupplier clock) {
        this.cdn = cdn.endsWith("/") ? cdn.substring(0, cdn.length() - 1) : cdn;
        this.fetch = fetch;
        this.background = background;
        this.clock = clock;
    }

    /** The one the game uses: ash's CDN, or the tests', over HTTP, on two background threads. */
    public static CapeSource forGame() {
        Executor executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "ash-cosmetics");
            thread.setDaemon(true);
            return thread;
        });
        return new CapeSource(System.getProperty(CDN_PROPERTY, DEFAULT_CDN), CapeSource::httpGet, executor,
                System::currentTimeMillis);
    }

    /** What {@code player} wears as a cape, if ash knows yet; never waits. */
    public Cape capeFor(UUID player) {
        Known known = profiles.get(player);
        if (known == null || clock.getAsLong() - known.at > FRESH_MS) {
            refresh(player);
        }
        if (known == null || known.capeUrl == null) {
            return null;
        }
        byte[] png = textures.get(known.capeUrl);
        return png == null ? null : new Cape(known.capeUrl, png);
    }

    private void refresh(UUID player) {
        if (!inFlight.add(player)) {
            return;
        }
        background.execute(() -> {
            try {
                byte[] profile = fetch.get(cdn + "/profiles/" + player.toString().replace("-", "") + ".json",
                        PROFILE_MAX_BYTES);
                String capeUrl = profile == null ? null : capeUrl(new String(profile, StandardCharsets.UTF_8), cdn);
                if (capeUrl != null && !textures.containsKey(capeUrl)) {
                    byte[] png = fetch.get(capeUrl, TEXTURE_MAX_BYTES);
                    if (png == null) {
                        capeUrl = null;
                    } else {
                        textures.put(capeUrl, png);
                    }
                }
                profiles.put(player, new Known(clock.getAsLong(), capeUrl));
            } catch (IOException | RuntimeException unreachable) {
                // Kept as it was, and asked again once stale. A cape that
                // can't be fetched is a cape not shown, never an error.
                Known before = profiles.get(player);
                profiles.put(player, new Known(clock.getAsLong(), before == null ? null : before.capeUrl));
            } finally {
                inFlight.remove(player);
            }
        });
    }

    /**
     * The cape's texture address in a profile, or null. Only an address on
     * ash's own CDN, under {@code cosmetics/}, is taken: a profile can't make
     * the game fetch from anywhere else.
     */
    static String capeUrl(String profile, String cdn) {
        Matcher m = CAPE_URL.matcher(profile);
        if (!m.find()) {
            return null;
        }
        String url = m.group(1);
        String prefix = cdn + "/cosmetics/";
        boolean plain = url.startsWith(prefix) && url.substring(prefix.length()).matches("[A-Za-z0-9._-]+\\.png");
        return plain ? url : null;
    }

    static byte[] httpGet(String url, int max) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "ash-client");
        try {
            int status = connection.getResponseCode();
            if (status == 404) {
                return null;
            }
            if (status != 200) {
                throw new IOException(url + " answered " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > max) {
                        throw new IOException(url + " is larger than " + max + " bytes");
                    }
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
