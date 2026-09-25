package com.ashlauncher.client.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
    private final Map<Feature, FeatureState> states = new EnumMap<>(Feature.class);

    public LoadReport(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public LoadReport with(Feature feature, FeatureState state) {
        states.put(feature, state);
        return this;
    }

    /** The features that did not load, in the order they are reported. */
    public List<Feature> degraded() {
        List<Feature> degraded = new ArrayList<>();
        for (Map.Entry<Feature, FeatureState> entry : states.entrySet()) {
            if (entry.getValue() == FeatureState.DEGRADED) {
                degraded.add(entry.getKey());
            }
        }
        return degraded;
    }

    /**
     * Writes the report, replacing the last session's.
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
        json.append("  \"features\": [\n");
        int written = 0;
        for (Map.Entry<Feature, FeatureState> entry : states.entrySet()) {
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
