package com.ashlauncher.client.hit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ashlauncher.client.settings.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HitColourTest {

    @TempDir
    Path configDir;

    @Test
    void the_defaults_are_the_games_own_flash_exactly_on_both_targets() {
        HitColour colour = new HitColour(Settings.load(configDir));

        // 1.21.11's OverlayTexture writes 0xB2FF0000 into its red rows.
        assertEquals(0xB2FF0000, colour.overlayTexel());
        // 1.8.9 puts 1, 0, 0 and 0.3F into its buffer.
        assertArrayEquals(new float[] {1F, 0F, 0F, 0.3F}, colour.envColour());
    }

    @Test
    void a_chosen_colour_and_strength_reach_both_forms() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.HIT_COLOUR_COLOUR, 0xFF3366FF);
        settings.set(Settings.HIT_COLOUR_STRENGTH, 60);
        HitColour colour = new HitColour(settings);

        // 60 per cent colour keeps 40 per cent of the entity's own: 102 of 255.
        assertEquals(0x663366FF, colour.overlayTexel());
        assertArrayEquals(new float[] {0x33 / 255F, 0x66 / 255F, 1F, 0.6F}, colour.envColour());
    }

    @Test
    void switched_off_it_is_the_games_flash_whatever_was_chosen() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.HIT_COLOUR_COLOUR, 0xFF00FF00);
        settings.set(Settings.HIT_COLOUR_STRENGTH, 90);
        settings.set(Settings.HIT_COLOUR, false);

        assertEquals(0xB2FF0000, new HitColour(settings).overlayTexel());
    }

    @Test
    void a_change_shows_at_once() {
        Settings settings = Settings.load(configDir);
        HitColour colour = new HitColour(settings);

        settings.set(Settings.HIT_COLOUR_STRENGTH, 100);

        assertEquals(0x00FF0000, colour.overlayTexel(), "a full-strength flash keeps none of the entity's colour");
    }

    @Test
    void no_strength_keeps_the_entity_as_it_is() {
        assertEquals(0xFFFF0000, HitColour.overlayTexel(0xFF0000, 0));
    }

    @Test
    void its_colour_is_always_opaque_however_it_is_written() throws IOException {
        Files.write(configDir.resolve("ash.properties"),
                "hit-colour.colour=#3366FF40\n".getBytes(StandardCharsets.UTF_8));

        Settings settings = Settings.load(configDir);

        assertEquals(0xFF3366FF, settings.get(Settings.HIT_COLOUR_COLOUR));
    }
}
