package com.ashlauncher.client.settings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import com.ashlauncher.client.report.Feature;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>The file is also an interface: a player can open it in a text editor,
 * and ash's settings screen changes it too. Two editors, one writer - the
 * client. That decides most of what follows.
 *
 * <ul>
 *   <li><b>Properties</b>, because it is in Java 8 - this runs on 1.8.9's JVM -
 *       tolerates the whitespace and comments a hand-edited file acquires,
 *       and needs no library the two targets would disagree about.
 *   <li><b>Written on a first run</b>, so the setting exists somewhere a
 *       player can find it. A default that lives only in code cannot be
 *       changed by anyone who does not read code.
 *   <li><b>Appended to, and changed only in place.</b> A setting the file
 *       does not have yet - because it was written by an older ash - is added
 *       at the end. A setting changed in game has its value rewritten on its
 *       own line and nothing else is touched, so the player's values,
 *       comments and ordering survive every upgrade and every change.
 *   <li><b>Written whole or not at all</b>, through a temporary file and a
 *       move, so a game that dies mid-write leaves the last complete file.
 *   <li><b>Never thrown from.</b> This runs while the game is starting, and a
 *       setting ash cannot read should cost the player that setting, not the
 *       session. What went wrong is in {@link #problems()}.
 * </ul>
 */
public final class Settings {

    /** In the loader's config directory, named for the mod id, as Fabric mods do. */
    static final String FILE_NAME = "ash.properties";

    public static final OnOff FPS_READOUT = new OnOff(Feature.FPS_READOUT, Category.HUD, "Your frame rate on screen.",
            "fps-readout.enabled", true,
            "Show the frame rate in the top-left corner. true or false.");

    public static final OnOff TOGGLE_SPRINT = new OnOff(Feature.TOGGLE_SPRINT, Category.MOVEMENT,
            "Keep sprinting after one press of a key, until the next.", "toggle-sprint.enabled", true,
            "Sprint on a key press instead of a held key. The key is in Options, Controls, Movement."
                    + " true or false.");

    public static final OnOff CROSSHAIR = new OnOff(Feature.CROSSHAIR, Category.PVP, "Your own crosshair in place of the game's.",
            "crosshair.enabled", true,
            "Draw ash's crosshair in place of the game's. true or false.");

    // The crosshair's options. Its default look is the plus Phase 2's
    // crosshair drew: four arms of four, no gap, one thick, white, outlined.

    public static final Choice CROSSHAIR_SHAPE = new Choice(Feature.CROSSHAIR, "crosshair.shape", "Shape", "cross",
            "The crosshair's shape: cross, t, dot or box.", "cross", "Cross", "t", "T", "dot", "Dot", "box", "Box");

    public static final Whole CROSSHAIR_SIZE = new Whole(Feature.CROSSHAIR, "crosshair.size", "Size", 4, 1, 10,
            "How long each arm of the crosshair is: a whole number from 1 to 10.");

    public static final Whole CROSSHAIR_GAP = new Whole(Feature.CROSSHAIR, "crosshair.gap", "Gap", 0, 0, 5,
            "How far the arms start from the centre: a whole number from 0 to 5.");

    public static final Whole CROSSHAIR_THICKNESS = new Whole(Feature.CROSSHAIR, "crosshair.thickness", "Thickness", 1,
            1, 4, "How thick the crosshair's lines are: a whole number from 1 to 4.");

    public static final Colour CROSSHAIR_COLOUR = new Colour(Feature.CROSSHAIR, "crosshair.colour", "Colour", 0xFFFFFFFF,
            "The crosshair's colour and opacity, as #RRGGBBAA. #FFFFFFFF is opaque white.");

    public static final OnOff CROSSHAIR_OUTLINE = new OnOff(Feature.CROSSHAIR, "Outline", "crosshair.outline", true,
            "A dark outline around the crosshair, so it shows against snow and sky. true or false.");

    /**
     * Every setting, in the order a first run writes them. One list, and
     * every setting is read, written and shown by walking it - so a setting
     * left out of it is not quietly read and never written, it has no value
     * at all, and the first test to ask for it fails. New settings go at the
     * end, which is where an older file gains them.
     */
    private static final List<Setting<?>> DECLARED = Collections.unmodifiableList(Arrays.<Setting<?>>asList(
            FPS_READOUT, TOGGLE_SPRINT, CROSSHAIR,
            CROSSHAIR_SHAPE, CROSSHAIR_SIZE, CROSSHAIR_GAP, CROSSHAIR_THICKNESS, CROSSHAIR_COLOUR, CROSSHAIR_OUTLINE));

    private final Path file;
    private final Map<Setting<?>, Object> values;
    private final List<String> problems;

    private Settings(Path file, Map<Setting<?>, Object> values, List<String> problems) {
        this.file = file;
        this.values = values;
        this.problems = Collections.unmodifiableList(problems);
    }

    /** Every setting there is, in the order the file lists them. */
    public static List<Setting<?>> declared() {
        return DECLARED;
    }

    /** Every feature's switch, in the order the file lists them: one card each on the settings screen. */
    public static List<OnOff> switches() {
        List<OnOff> switches = new ArrayList<>();
        for (Setting<?> setting : DECLARED) {
            if (setting instanceof OnOff && ((OnOff) setting).isSwitch()) {
                switches.add((OnOff) setting);
            }
        }
        return Collections.unmodifiableList(switches);
    }

    /** A feature's options, in the order the file lists them: everything that belongs to it but its switch. */
    public static List<Setting<?>> optionsOf(Feature feature) {
        List<Setting<?>> options = new ArrayList<>();
        for (Setting<?> setting : DECLARED) {
            boolean isSwitch = setting instanceof OnOff && ((OnOff) setting).isSwitch();
            if (setting.feature() == feature && !isSwitch) {
                options.add(setting);
            }
        }
        return Collections.unmodifiableList(options);
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

        Map<Setting<?>, Object> values = new IdentityHashMap<>();
        for (Setting<?> setting : DECLARED) {
            values.put(setting, read(properties, setting, problems));
        }
        return new Settings(file, values, problems);
    }

    /**
     * Whether the player has a feature on: its switch's value, or true for a
     * feature that has no switch.
     */
    public boolean on(Feature feature) {
        for (OnOff setting : switches()) {
            if (setting.feature() == feature) {
                return get(setting);
            }
        }
        return true;
    }

    /** The setting's value this session: as read, or as last changed in game. */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        return (T) values.get(setting);
    }

    /**
     * Changes a setting for this session and in the file, rewriting its value
     * in place and nothing else.
     *
     * <p>The change is made to the file as it is now, not as it was when the
     * game started, so an edit the player made by hand in the meantime
     * survives it. A setting the file has lost is added back at the end.
     *
     * <p>The file is edited as bytes, seen one character per byte (ISO-8859-1),
     * not as UTF-8. Everything the edit looks for - keys, separators, line
     * endings, backslashes - is ASCII, and ASCII bytes mean the same in UTF-8
     * and in whatever "ANSI" a Windows editor saved, so the edit finds the
     * same line either way. Every other byte goes back exactly as it came,
     * which a decode to UTF-8 and back would not promise for a file that is
     * not UTF-8.
     *
     * <p>Never thrown from. When the file cannot be changed safely it is left
     * exactly as it was, the change lasts until the game closes, and what to
     * do about it comes back for whoever asked to say so.
     *
     * <p>A value outside what its kind allows is brought within it first - a
     * colour too faint to draw alike on both targets, say - and kept as that.
     */
    public <T> Saved set(Setting<T> setting, T wanted) {
        T value = setting.normalise(wanted);
        values.put(setting, value);
        try {
            byte[] current = Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
            String text = new String(current, StandardCharsets.ISO_8859_1);
            new Properties().load(new StringReader(text));

            String changed = PropertiesText.withValue(text, setting.key(), setting.format(value));
            writeWhole(file, changed != null
                    ? changed.getBytes(StandardCharsets.ISO_8859_1)
                    : appended(current, entry(setting, value)));
            return Saved.SAVED;
        } catch (IllegalArgumentException unreadable) {
            return Saved.FILE_UNREADABLE;
        } catch (IOException unwritable) {
            return Saved.FILE_UNWRITABLE;
        }
    }

    /**
     * What was wrong with the file when the game started, one sentence each,
     * for the game's log.
     *
     * <p>Never thrown: a setting ash cannot read costs the player that setting,
     * not their session.
     */
    public List<String> problems() {
        return problems;
    }

    /** Adds every setting the file lacks to its end. */
    private static void appendMissing(Path file, byte[] original, Properties properties, List<String> problems) {
        StringBuilder missing = new StringBuilder();
        for (Setting<?> setting : DECLARED) {
            if (!properties.containsKey(setting.key())) {
                missing.append(fallbackEntry(setting));
            }
        }
        if (missing.length() == 0) {
            return;
        }

        try {
            writeWhole(file, appended(original, missing.toString()));
        } catch (IOException unwritable) {
            problems.add(FILE_NAME + " could not be written (" + unwritable
                    + "), so settings it lacks are at their defaults and will not appear in it");
        }
    }

    /** A setting as a first run writes it: its comment, then its line. */
    private static <T> String entry(Setting<T> setting, T value) {
        return "# " + setting.comment() + "\n" + setting.key() + "=" + setting.format(value) + "\n";
    }

    private static <T> String fallbackEntry(Setting<T> setting) {
        return entry(setting, setting.fallback());
    }

    /**
     * The file's bytes with {@code entries} after them, on lines of their own.
     *
     * <p>A hand-edited file often has no newline at the end, and appending to
     * one would glue the first new setting onto the player's last line.
     */
    private static byte[] appended(byte[] file, String entries) {
        boolean endsMidLine = file.length > 0 && file[file.length - 1] != '\n';
        byte[] added = ((endsMidLine ? "\n" : "") + entries).getBytes(StandardCharsets.UTF_8);
        byte[] whole = Arrays.copyOf(file, file.length + added.length);
        System.arraycopy(added, 0, whole, file.length, added.length);
        return whole;
    }

    /**
     * Through a temporary file and a move, so that a game that dies while this
     * is being written leaves the last complete file, never half of one.
     */
    private static void writeWhole(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        Files.write(partial, bytes);
        try {
            try {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException noAtomicMove) {
                // A file system that cannot move atomically still gets its
                // settings - a plain append always worked there before.
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException notMoved) {
            // Not left lying in the player's config folder for them to wonder about.
            Files.deleteIfExists(partial);
            throw notMoved;
        }
    }

    /**
     * The setting's value from the file, or its default - with a sentence in
     * {@code problems} when the file has a value ash cannot use, so that a
     * mistake costs the player that one setting and says so.
     */
    private static <T> T read(Properties properties, Setting<T> setting, List<String> problems) {
        String raw = properties.getProperty(setting.key());
        if (raw == null) {
            return setting.fallback();
        }
        T value = setting.parse(raw);
        if (value != null) {
            return value;
        }
        problems.add(setting.key() + " is \"" + raw + "\" in " + FILE_NAME + ", which is not " + setting.expected()
                + ", so it is " + setting.format(setting.fallback()) + " until that is fixed");
        return setting.fallback();
    }
}
