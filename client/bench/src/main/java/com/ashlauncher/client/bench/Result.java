package com.ashlauncher.client.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What one run of the scene found, written where it can be kept: a JSON file
 * with the summaries, the spread between passes, the machine, ash's settings
 * and the profile, and a CSV beside it with every frame's time.
 *
 * <p>The spread is how far the passes disagree, as a percentage of their
 * mean. Two runs can only be told apart by more than that: a change that
 * moves the average by less than the spread has not been shown to move it
 * at all.
 */
public final class Result {

    /**
     * How far a run's passes may disagree, in per cent, for the run to be
     * compared with another. Past it, something else was using the machine.
     */
    public static final double COMPARABLE_SPREAD_PERCENT = 3.0;

    /** One part of a frame, from the game's own profiler: its path and its share of the frame. */
    public static final class Section {
        private final String path;
        private final double percent;

        public Section(String path, double percent) {
            this.path = path;
            this.percent = percent;
        }

        public String path() {
            return path;
        }

        public double percent() {
            return percent;
        }
    }

    private final String target;
    private final String label;
    private final String recorded;
    private final Map<String, String> machine;
    private final Map<String, String> settings;
    private final List<long[]> passes;
    private final List<Section> profile;

    /**
     * @param target the version target, such as {@code 1.8.9}
     * @param label what this run is, such as {@code baseline} or {@code lithium}
     * @param recorded when, as an ISO 8601 instant
     * @param machine what it ran on: the processor, the graphics card and
     *     driver, the operating system and Java
     * @param settings ash's settings for the run, key by key, so a run with a
     *     feature off is never mistaken for one with it on
     * @param passes each pass's frame times, in nanoseconds
     * @param profile where the frame went, or empty for a target with no
     *     profiler to read
     */
    public Result(String target, String label, String recorded, Map<String, String> machine,
            Map<String, String> settings, List<long[]> passes, List<Section> profile) {
        this.target = target;
        this.label = label;
        this.recorded = recorded;
        this.machine = Collections.unmodifiableMap(new LinkedHashMap<>(machine));
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        this.passes = Collections.unmodifiableList(new ArrayList<>(passes));
        this.profile = Collections.unmodifiableList(new ArrayList<>(profile));
    }

    public Summary overall() {
        return Summary.ofAll(passes);
    }

    /** How far the passes' average frame rates disagree, as a percentage of their mean. */
    public double averageSpreadPercent() {
        List<Double> averages = new ArrayList<>();
        for (long[] pass : passes) {
            averages.add(Summary.of(pass).averageFps());
        }
        return spreadPercent(averages);
    }

    /** The same for their 1% lows, which always disagree more. */
    public double onePercentLowSpreadPercent() {
        List<Double> lows = new ArrayList<>();
        for (long[] pass : passes) {
            lows.add(Summary.of(pass).onePercentLowFps());
        }
        return spreadPercent(lows);
    }

    /** Whether the passes agree closely enough for this run to be compared with another. */
    public boolean comparable() {
        return averageSpreadPercent() <= COMPARABLE_SPREAD_PERCENT;
    }

    /**
     * The standard deviation over the mean, in per cent: the sample's, since
     * a run's passes are a sample of what the machine does. 0 for one pass.
     */
    static double spreadPercent(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.size();
        double squares = 0;
        for (double value : values) {
            squares += (value - mean) * (value - mean);
        }
        return Math.sqrt(squares / (values.size() - 1)) / mean * 100;
    }

    /** One line for the log: what a developer reads first. */
    public String oneLine() {
        Summary overall = overall();
        return String.format(Locale.ROOT, "%s %s: %.1f FPS average, %.1f FPS 1%% low, over %d passes"
                + " that agree within %.1f%%%s", target, label, overall.averageFps(), overall.onePercentLowFps(),
                passes.size(), averageSpreadPercent(),
                comparable() ? "" : " - too far apart to compare; something else was using the machine");
    }

    public String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"target\": ").append(string(target)).append(",\n");
        out.append("  \"label\": ").append(string(label)).append(",\n");
        out.append("  \"recorded\": ").append(string(recorded)).append(",\n");
        out.append("  \"scene\": {\"seed\": ").append(Scene.SEED)
                .append(", \"x\": ").append(number(Scene.X))
                .append(", \"y\": ").append(number(Scene.Y))
                .append(", \"z\": ").append(number(Scene.Z))
                .append(", \"pitch\": ").append(number(Scene.PITCH))
                .append(", \"renderDistance\": ").append(Scene.RENDER_DISTANCE)
                .append(", \"width\": ").append(Scene.WIDTH)
                .append(", \"height\": ").append(Scene.HEIGHT)
                .append(", \"guiScale\": ").append(Scene.GUI_SCALE)
                .append(", \"warmUpSeconds\": ").append(Scene.WARM_UP_SECONDS)
                .append(", \"passSeconds\": ").append(Scene.PASS_SECONDS)
                .append("},\n");
        out.append("  \"machine\": ").append(object(machine)).append(",\n");
        out.append("  \"ashSettings\": ").append(object(settings)).append(",\n");
        out.append("  \"overall\": ").append(summary(overall())).append(",\n");
        out.append("  \"spread\": {\"averageFpsPercent\": ").append(number(averageSpreadPercent()))
                .append(", \"onePercentLowFpsPercent\": ").append(number(onePercentLowSpreadPercent()))
                .append(", \"comparable\": ").append(comparable()).append("},\n");
        out.append("  \"passes\": [\n");
        for (int i = 0; i < passes.size(); i++) {
            out.append("    ").append(summary(Summary.of(passes.get(i))))
                    .append(i < passes.size() - 1 ? ",\n" : "\n");
        }
        out.append("  ],\n");
        out.append("  \"profile\": [");
        for (int i = 0; i < profile.size(); i++) {
            Section section = profile.get(i);
            out.append(i == 0 ? "\n" : ",\n").append("    {\"section\": ").append(string(section.path))
                    .append(", \"percentOfFrame\": ").append(number(section.percent)).append("}");
        }
        out.append(profile.isEmpty() ? "]\n" : "\n  ]\n");
        out.append("}\n");
        return out.toString();
    }

    /** Every frame: its pass, its place in the pass, and its time in milliseconds. */
    public String framesCsv() {
        StringBuilder out = new StringBuilder("pass,frame,ms\n");
        for (int pass = 0; pass < passes.size(); pass++) {
            long[] frames = passes.get(pass);
            for (int frame = 0; frame < frames.length; frame++) {
                out.append(pass + 1).append(',').append(frame + 1).append(',')
                        .append(String.format(Locale.ROOT, "%.3f", frames[frame] / 1e6)).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Writes the JSON and the CSV into {@code directory}, named for the
     * target, the label and {@code stamp}, and returns the JSON's path.
     */
    public Path write(Path directory, String stamp) throws IOException {
        Files.createDirectories(directory);
        String name = target + "-" + safe(label) + "-" + stamp;
        Path json = directory.resolve(name + ".json");
        Files.write(json, json().getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve(name + "-frames.csv"), framesCsv().getBytes(StandardCharsets.UTF_8));
        return json;
    }

    /** A label as a file name: letters, digits, dots and dashes. */
    static String safe(String label) {
        String cleaned = label.replaceAll("[^A-Za-z0-9.-]+", "-");
        return cleaned.isEmpty() ? "run" : cleaned;
    }

    private static String summary(Summary summary) {
        return "{\"frames\": " + summary.frames()
                + ", \"seconds\": " + number(summary.seconds())
                + ", \"averageFps\": " + number(summary.averageFps())
                + ", \"averageMs\": " + number(summary.averageMs())
                + ", \"onePercentLowFps\": " + number(summary.onePercentLowFps())
                + ", \"worstMs\": " + number(summary.worstMs()) + "}";
    }

    private static String object(Map<String, String> values) {
        if (values.isEmpty()) {
            return "{}";
        }
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out.append(first ? "\n" : ",\n").append("    ").append(string(entry.getKey())).append(": ")
                    .append(string(entry.getValue()));
            first = false;
        }
        return out.append("\n  }").toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** A JSON string: quoted, with the characters JSON forbids escaped. */
    static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
