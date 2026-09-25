package com.ashlauncher.client.settings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The client's own settings, in its own file in the game directory.
 *
 * <p>The client owns this file and nothing else writes it - the launcher least
 * of all, which is a rule of this phase rather than an accident of it. Synced
 * settings are Phase 4, and the conflict rules that come with a second writer
 * belong to that design; inventing the format early would mean inventing it
 * twice. See {@code docs/specs/0002-phase-2-client.md}.
 *
 * <p>There is no in-game editor yet, so the file is also the interface: a
 * player opens it in a text editor. That decides most of what follows.
 *
 * <ul>
 *   <li><b>Properties</b>, because it is in Java 8 - this runs on 1.8.9's JVM -
 *       tolerates the whitespace and comments a hand-edited file acquires,
 *       and needs no library the two targets would disagree about.
 *   <li><b>Written on a first run</b>, so the setting exists somewhere a
 *       player can find it. A default that lives only in code cannot be
 *       changed by anyone who does not read code.
 *   <li><b>Appended to, never rewritten.</b> A setting the file does not have
 *       yet - because it was written by an older ash - is added at the end.
 *       Nothing already there is touched, so the player's values, comments
 *       and ordering survive every upgrade.
 *   <li><b>Never thrown from.</b> This runs while the game is starting, and a
 *       setting ash cannot read should cost the player that setting, not the
 *       session. What went wrong is in {@link #problems()}.
 * </ul>
 */
public final class Settings {

    /** In the loader's config directory, named for the mod id, as Fabric mods do. */
    static final String FILE_NAME = "ash.properties";

    static final String FPS_READOUT_ENABLED = "fps-readout.enabled";

    static final String TOGGLE_SPRINT_ENABLED = "toggle-sprint.enabled";

    private static final Flag FPS_READOUT = new Flag(FPS_READOUT_ENABLED, true,
            "Show the frame rate in the top-left corner. true or false.");

    private static final Flag TOGGLE_SPRINT = new Flag(TOGGLE_SPRINT_ENABLED, true,
            "Sprint on a key press instead of a held key. The key is in Options, Controls, Movement."
                    + " true or false.");

    /**
     * Every setting, in the order a first run writes them. One list, so that
     * a setting cannot be read without also being written into a file that
     * lacks it.
     */
    private static final Flag[] FLAGS = {FPS_READOUT, TOGGLE_SPRINT};

    private final boolean fpsReadoutEnabled;
    private final boolean toggleSprintEnabled;
    private final List<String> problems;

    private Settings(boolean fpsReadoutEnabled, boolean toggleSprintEnabled, List<String> problems) {
        this.fpsReadoutEnabled = fpsReadoutEnabled;
        this.toggleSprintEnabled = toggleSprintEnabled;
        this.problems = Collections.unmodifiableList(problems);
    }

    /**
     * Reads the settings from {@code configDir}, writing any the file lacks.
     *
     * @param configDir the loader's config directory, inside the game directory
     */
    public static Settings load(Path configDir) {
        List<String> problems = new ArrayList<>();
        Path file = configDir.resolve(FILE_NAME);

        byte[] original = new byte[0];
        Properties properties = new Properties();
        try {
            if (Files.exists(file)) {
                original = Files.readAllBytes(file);
            }
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(original), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException | IllegalArgumentException unreadable) {
            // IllegalArgumentException is what `Properties` throws on a
            // malformed unicode escape, and a Windows path is one: `C:\` then
            // `users`. A hand-edited file can easily have one.
            //
            // Everything read before the bad line is thrown away with it. Keeping
            // it would make which settings survive depend on where in the file
            // the mistake happened to be, which no player could predict.
            properties = new Properties();
            problems.add(FILE_NAME + " could not be read (" + unreadable + "), so every setting is at its default");
        }

        if (problems.isEmpty()) {
            // Never written to when it could not be read: appending to a file
            // ash does not understand is how a player's file gets damaged.
            appendMissing(file, original, properties, problems);
        }

        return new Settings(
                read(properties, FPS_READOUT, problems),
                read(properties, TOGGLE_SPRINT, problems),
                problems);
    }

    public boolean fpsReadoutEnabled() {
        return fpsReadoutEnabled;
    }

    public boolean toggleSprintEnabled() {
        return toggleSprintEnabled;
    }

    /**
     * What was wrong with the file, one sentence each, for the game's log.
     *
     * <p>Never thrown: a setting ash cannot read costs the player that setting,
     * not their session.
     */
    public List<String> problems() {
        return problems;
    }

    /**
     * Adds every setting the file lacks to its end, on lines of their own.
     *
     * <p>A hand-edited file often has no newline at the end, and appending to
     * one would glue the first new setting onto the player's last line.
     */
    private static void appendMissing(Path file, byte[] original, Properties properties, List<String> problems) {
        StringBuilder missing = new StringBuilder();
        for (Flag flag : FLAGS) {
            if (!properties.containsKey(flag.key)) {
                missing.append("# ").append(flag.comment).append('\n')
                        .append(flag.key).append('=').append(flag.fallback).append('\n');
            }
        }
        if (missing.length() == 0) {
            return;
        }

        boolean endsMidLine = original.length > 0 && original[original.length - 1] != '\n';
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, ((endsMidLine ? "\n" : "") + missing).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException unwritable) {
            problems.add(FILE_NAME + " could not be written (" + unwritable
                    + "), so settings it lacks are at their defaults and will not appear in it");
        }
    }

    /**
     * {@code true} or {@code false}, in any case, and nothing else.
     *
     * <p>Strict because the lenient reading is a trap: {@code Boolean.parseBoolean}
     * calls anything that is not "true" false, so a player who writes "yes"
     * would switch the feature off without a word.
     */
    private static boolean read(Properties properties, Flag flag, List<String> problems) {
        String value = properties.getProperty(flag.key);
        if (value == null) {
            return flag.fallback;
        }
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        problems.add(flag.key + " is \"" + value + "\" in " + FILE_NAME
                + ", which is neither true nor false, so it is " + flag.fallback + " until that is fixed");
        return flag.fallback;
    }

    /** One on/off setting: its key, what it is when unset, and what it does. */
    private static final class Flag {
        final String key;
        final boolean fallback;
        final String comment;

        Flag(String key, boolean fallback, String comment) {
            this.key = key;
            this.fallback = fallback;
            this.comment = comment;
        }
    }
}
