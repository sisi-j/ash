package com.ashlauncher.client.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What this session's start made of each of ash's features, for the launcher.
 *
 * <p>Written as the client starts, into {@code ash/load-report.json} in the
 * game directory, replacing the last session's. The launcher reads it before
 * the next play and tells the player about any feature that degraded. The
 * channel runs one way: nothing here reads what the launcher thinks, and the
 * launcher never writes this file. See ADR-0017.
 *
 * <p>The format is pinned by one example file both sides test against,
 * {@code launcher/core/tests/fixtures/load-report.json}: the launcher's tests
 * parse it and this class's test must produce it exactly.
 *
 * <p>JSON by hand rather than through a library, because the one library both
 * games ship is Gson at two very different versions, and what is written here
 * is a handful of known keys and strings.
 */
public final class LoadReport {

    /** Relative to the game directory. The launcher reads it from here. */
    public static final String RELATIVE_PATH = "ash/load-report.json";

    private final String clientVersion;
    private final Map<Feature, FeatureStatus> states = new EnumMap<>(Feature.class);
    /** Whether any of the player's own mods loaded this session. */
    private boolean thirdPartyMods;
    /** Whose copy of each of ash's bundled mods loaded, by mod id. */
    private final Map<String, BundledCopy> bundled = new LinkedHashMap<>();

    public LoadReport(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    /**
     * The report on this session: every feature there is, each from whether
     * its mixins landed and whether the player has it on.
     *
     * <p>Walked rather than listed, so a feature added to {@link Feature} is
     * in every report without either target's entrypoint having to name it.
     *
     * @param landed whether a feature's mixins landed; true for one with none
     * @param on whether the player has it on; true for one with no switch
     */
    public static LoadReport forSession(String clientVersion, Predicate<Feature> landed, Predicate<Feature> on) {
        LoadReport report = new LoadReport(clientVersion);
        for (Feature feature : Feature.values()) {
            report.with(feature, FeatureStatus.of(on.test(feature), landed.test(feature)));
        }
        return report;
    }

    /** Whether any of the player's own mods loaded, and whose copy of each bundled mod won. */
    public LoadReport withOrigins(ModOrigins origins, List<String> bundledIds) {
        thirdPartyMods = origins.thirdPartyModsLoaded();
        bundled.putAll(origins.bundled(bundledIds));
        return this;
    }

    public LoadReport withThirdPartyMods(boolean loaded) {
        thirdPartyMods = loaded;
        return this;
    }

    public LoadReport withBundled(String id, BundledCopy copy) {
        bundled.put(id, copy);
        return this;
    }

    public LoadReport with(Feature feature, FeatureStatus state) {
        states.put(feature, state);
        return this;
    }

    /** The features that did not load, in the order they are reported. */
    public List<Feature> degraded() {
        List<Feature> degraded = new ArrayList<>();
        for (Map.Entry<Feature, FeatureStatus> entry : states.entrySet()) {
            if (entry.getValue() == FeatureStatus.DEGRADED) {
                degraded.add(entry.getKey());
            }
        }
        return degraded;
    }

    /**
     * Writes the report, replacing the last one - at startup, and again
     * whenever the player changes a setting in game, so that it says what
     * the session ended with.
     *
     * <p>Through a temporary file and a move, so that a game that dies while
     * this is being written leaves the last complete report or this one, and
     * never half of either for the launcher to trip over.
     *
     * @throws IOException for the caller to log; never worth stopping the game over
     */
    public void writeTo(Path gameDir) throws IOException {
        Path file = gameDir.resolve(RELATIVE_PATH);
        Files.createDirectories(file.getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        Files.write(partial, toJson().getBytes(StandardCharsets.UTF_8));
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"client\": ").append(quoted(clientVersion)).append(",\n");
        json.append("  \"third_party_mods\": ").append(thirdPartyMods).append(",\n");
        json.append("  \"bundled\": [");
        int listed = 0;
        for (Map.Entry<String, BundledCopy> mod : bundled.entrySet()) {
            json.append(listed++ == 0 ? "\n" : ",\n");
            json.append("    { \"id\": ").append(quoted(mod.getKey()))
                    .append(", \"copy\": ").append(quoted(mod.getValue().word())).append(" }");
        }
        json.append(listed == 0 ? "],\n" : "\n  ],\n");
        json.append("  \"features\": [\n");
        int written = 0;
        for (Map.Entry<Feature, FeatureStatus> entry : states.entrySet()) {
            json.append("    { \"id\": ").append(quoted(entry.getKey().id()))
                    .append(", \"name\": ").append(quoted(entry.getKey().displayName()))
                    .append(", \"status\": ").append(quoted(entry.getValue().word()))
                    .append(" }");
            json.append(++written < states.size() ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /** A JSON string: quotes, backslashes and control characters escaped. */
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
}
