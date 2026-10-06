package com.ashlauncher.client.bench;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * What a run takes from where it runs: its label and where to write, passed
 * by Gradle as system properties; the time; ash's settings; and the machine.
 * The same on both targets, so each target only adds what its own game
 * knows - the graphics card and driver.
 */
public final class Runs {

    /** {@code -Pbench.label=lithium} on the command line; Gradle passes it on as this. */
    public static final String LABEL_PROPERTY = "ash.bench.label";

    /** Where results go; Gradle passes the client's {@code benchmark-results} directory. */
    public static final String OUTPUT_PROPERTY = "ash.bench.out";

    private Runs() {
    }

    /** What this run is called: {@code baseline} unless the command says otherwise. */
    public static String label() {
        String label = System.getProperty(LABEL_PROPERTY, "").trim();
        return label.isEmpty() ? "baseline" : label;
    }

    /** Where to write results: as Gradle says, or {@code benchmark-results} in the game directory. */
    public static Path outputDirectory(Path gameDirectory) {
        String out = System.getProperty(OUTPUT_PROPERTY, "").trim();
        return out.isEmpty() ? gameDirectory.resolve("benchmark-results") : Paths.get(out);
    }

    /** {@code 20261006-154210}, in UTC: sorts in the order runs were made. */
    public static String stamp(long epochMillis) {
        return format("yyyyMMdd-HHmmss", epochMillis);
    }

    /** {@code 2026-10-06T15:42:10Z}. */
    public static String instant(long epochMillis) {
        return format("yyyy-MM-dd'T'HH:mm:ss'Z'", epochMillis);
    }

    private static String format(String pattern, long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, java.util.Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    /**
     * ash's settings as the run found them, key by key in order: what was on
     * and off is part of what was measured. Empty when there is no file yet.
     */
    public static Map<String, String> ashSettings(Path configDirectory) {
        Path file = configDirectory.resolve("ash.properties");
        Map<String, String> settings = new TreeMap<>();
        if (!Files.exists(file)) {
            return settings;
        }
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException unreadable) {
            settings.put("unreadable", unreadable.toString());
            return settings;
        }
        for (String key : properties.stringPropertyNames()) {
            settings.put(key, properties.getProperty(key));
        }
        return settings;
    }

    /** The machine: the target's answers about the graphics, then what Java knows. */
    public static Map<String, String> machine(String cpu, String gpu, String gpuVendor, String driver) {
        Map<String, String> machine = new LinkedHashMap<>();
        machine.put("cpu", cpu);
        machine.put("cores", Integer.toString(Runtime.getRuntime().availableProcessors()));
        machine.put("gpu", gpu);
        machine.put("gpuVendor", gpuVendor);
        machine.put("driver", driver);
        machine.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        machine.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        machine.put("maxMemoryMb", Long.toString(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        return machine;
    }
}
