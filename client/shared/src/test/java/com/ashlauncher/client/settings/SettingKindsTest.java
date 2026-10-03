package com.ashlauncher.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The kinds of setting beyond on/off - a choice, a whole number, a colour -
 * as the file holds them: read, checked, defaulted, and changed in place.
 */
class SettingKindsTest {

    @TempDir
    Path configDir;

    private Settings withLine(String key, String value) throws IOException {
        Settings.load(configDir);
        Path file = configDir.resolve("ash.properties");
        String text = Files.readString(file);
        int start = text.indexOf("\n" + key + "=") + 1;
        int end = text.indexOf('\n', start);
        Files.writeString(file, text.substring(0, start) + key + "=" + value + text.substring(end));
        return Settings.load(configDir);
    }

    @Test
    void a_whole_number_in_range_is_read_and_one_out_of_range_or_malformed_is_the_default_and_reported()
            throws IOException {
        assertEquals(7, withLine("crosshair.size", "7").get(Settings.CROSSHAIR_SIZE));

        for (String bad : new String[] {"11", "0", "-3", "four", "4.5", ""}) {
            Settings settings = withLine("crosshair.size", bad);

            assertEquals(Settings.CROSSHAIR_SIZE.fallback(), settings.get(Settings.CROSSHAIR_SIZE), "for \"" + bad + "\"");
            assertTrue(settings.problems().stream().anyMatch(p -> p.contains("crosshair.size") && p.contains("from 1 to 10")),
                    "for \"" + bad + "\": " + settings.problems());
        }
    }

    @Test
    void a_choice_is_read_in_any_case_and_an_unknown_one_is_the_default_and_reported() throws IOException {
        assertEquals("dot", withLine("crosshair.shape", " DOT ").get(Settings.CROSSHAIR_SHAPE));

        Settings settings = withLine("crosshair.shape", "circle");

        assertEquals(Settings.CROSSHAIR_SHAPE.fallback(), settings.get(Settings.CROSSHAIR_SHAPE));
        assertTrue(settings.problems().stream().anyMatch(p -> p.contains("circle") && p.contains("cross, t, dot or box")),
                settings.problems().toString());
    }

    @Test
    void a_colour_is_read_with_or_without_its_opacity() throws IOException {
        assertEquals(0xFFFF4D4D, (int) withLine("crosshair.colour", "#FF4D4D").get(Settings.CROSSHAIR_COLOUR));
        assertEquals(0x804DC3FF, (int) withLine("crosshair.colour", "#4dc3ff80").get(Settings.CROSSHAIR_COLOUR));
    }

    @Test
    void a_colour_too_faint_for_both_targets_to_draw_alike_is_raised_to_the_faintest_they_agree_on()
            throws IOException {
        // At an opacity near zero, 1.8.9 draws fully opaque and 1.21.11 draws
        // nothing: the same setting would look opposite on the two.
        int read = withLine("crosshair.colour", "#FFFFFF00").get(Settings.CROSSHAIR_COLOUR);
        assertEquals(Colour.MIN_ALPHA, read >>> 24, "a near-invisible colour read from the file");

        Settings settings = Settings.load(configDir);
        settings.set(Settings.CROSSHAIR_COLOUR, 0x02FFFFFF);
        assertEquals(Colour.MIN_ALPHA, settings.get(Settings.CROSSHAIR_COLOUR) >>> 24, "a near-invisible colour set");
        assertEquals(Colour.MIN_ALPHA, Settings.load(configDir).get(Settings.CROSSHAIR_COLOUR) >>> 24, "and saved");
    }

    @Test
    void a_malformed_colour_is_the_default_and_reported() throws IOException {
        for (String bad : new String[] {"white", "#FFF", "#GGGGGG", "FF4D4D4D4D"}) {
            Settings settings = withLine("crosshair.colour", bad);

            assertEquals(Settings.CROSSHAIR_COLOUR.fallback(), settings.get(Settings.CROSSHAIR_COLOUR), "for \"" + bad + "\"");
            assertTrue(settings.problems().stream().anyMatch(p -> p.contains("crosshair.colour")), settings.problems().toString());
        }
    }

    @Test
    void every_kind_is_changed_in_place_and_read_back_as_set() throws IOException {
        Settings settings = Settings.load(configDir);

        settings.set(Settings.CROSSHAIR_SHAPE, "box");
        settings.set(Settings.CROSSHAIR_SIZE, 9);
        settings.set(Settings.CROSSHAIR_COLOUR, 0xC04DFF88);
        settings.set(Settings.CROSSHAIR_OUTLINE, false);

        String file = Files.readString(configDir.resolve("ash.properties"));
        for (String line : new String[] {"crosshair.shape=box", "crosshair.size=9", "crosshair.colour=#4DFF88C0",
                "crosshair.outline=false"}) {
            assertTrue(file.contains("\n" + line + "\n"), line + " is not in the file:\n" + file);
        }
        Settings next = Settings.load(configDir);
        assertEquals("box", next.get(Settings.CROSSHAIR_SHAPE));
        assertEquals(9, next.get(Settings.CROSSHAIR_SIZE));
        assertEquals(0xC04DFF88, (int) next.get(Settings.CROSSHAIR_COLOUR));
        assertEquals(false, next.get(Settings.CROSSHAIR_OUTLINE));
        assertEquals(java.util.List.of(), next.problems());
    }
}
