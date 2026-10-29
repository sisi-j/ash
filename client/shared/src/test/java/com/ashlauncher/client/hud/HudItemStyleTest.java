package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.game.FakeGameView;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every HUD item's style, one group of options for all of them (spec 0007, HUD items). */
class HudItemStyleTest {

    @TempDir
    Path configDir;

    private final AtomicLong millis = new AtomicLong();
    private Settings settings;
    private HudLayout layout;

    private HudItems items() {
        settings = settings == null ? Settings.load(configDir) : settings;
        layout = new HudLayout(settings);
        return new HudItems(layout, new FakeGameView().withFramesPerSecond(144).withLatency(42), true, millis::get);
    }

    private Settings settings() {
        settings = Settings.load(configDir);
        return settings;
    }

    /** The FPS item, as it draws on a typical screen. */
    private FakeHudSurface.Text fps() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        items().draw(HudItem.FPS, surface);
        return surface.onlyText();
    }

    @Test
    void left_alone_it_looks_as_the_readouts_always_have() {
        FakeHudSurface.Text drawn = fps();

        assertEquals("144 FPS", drawn.text());
        assertEquals(4, drawn.x());
        assertEquals(4, drawn.y());
        assertEquals(HudItems.COLOUR, drawn.colour());
        assertEquals(1f, drawn.scale());
        assertTrue(drawn.shadow());
    }

    @Test
    void its_size_scales_the_text_and_the_box_edit_hud_outlines() {
        settings().set(Settings.FPS_READOUT_ITEM.size, 200);

        FakeHudSurface.Text drawn = fps();

        assertEquals(2f, drawn.scale());
        // "144 FPS" is seven characters of six on the fake's font.
        assertEquals(84, layout.width(HudItem.FPS));
        assertEquals(18, layout.height(HudItem.FPS));
    }

    @Test
    void its_text_takes_the_colour_the_player_picked() {
        settings().set(Settings.FPS_READOUT_ITEM.textColour, 0xFF4DC3FF);

        assertEquals(0xFF4DC3FF, fps().colour());
    }

    @Test
    void chroma_goes_round_the_rainbow_at_one_speed_for_every_item_and_keeps_the_picked_colour() {
        settings().set(Settings.FPS_READOUT_ITEM.textColour, 0xFF4DC3FF);
        settings.set(Settings.FPS_READOUT_ITEM.chroma, true);
        settings.set(Settings.PING_READOUT_ITEM.chroma, true);
        HudItems items = items();

        FakeHudSurface start = FakeHudSurface.ofTypicalSize();
        items.draw(start);
        millis.set(HudItems.CHROMA_MILLIS / 4);
        FakeHudSurface quarter = FakeHudSurface.ofTypicalSize();
        items.draw(quarter);
        millis.set(HudItems.CHROMA_MILLIS);
        FakeHudSurface round = FakeHudSurface.ofTypicalSize();
        items.draw(round);

        assertEquals(0xFFFF0000, start.drawn().get(0).colour(), "the rainbow starts at red");
        assertNotEquals(start.drawn().get(0).colour(), quarter.drawn().get(0).colour(), "the colour did not move");
        assertEquals(start.drawn().get(0).colour(), round.drawn().get(0).colour(), "once round is back where it was");
        assertEquals(quarter.drawn().get(0).colour(), quarter.drawn().get(1).colour(),
                "the frame rate and the ping cycle apart");
        assertEquals(0xFF4DC3FF, settings.get(Settings.FPS_READOUT_ITEM.textColour), "chroma lost the picked colour");
    }

    @Test
    void a_background_is_a_box_round_the_text_in_its_own_colour() {
        settings().set(Settings.FPS_READOUT_ITEM.background, true);
        settings.set(Settings.FPS_READOUT_ITEM.backgroundColour, 0x80102030);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        items().draw(HudItem.FPS, surface);

        FakeHudSurface.Fill box = surface.fills().get(0);
        FakeHudSurface.Text text = surface.onlyText();
        assertEquals(List.of(4, 4, 42 + 2 * HudItems.PAD, 9 + 2 * HudItems.PAD, 0x80102030),
                List.of(box.x(), box.y(), box.width(), box.height(), box.colour()));
        assertEquals(4 + HudItems.PAD, text.x(), "the text is not inside its box");
        assertEquals(4 + HudItems.PAD, text.y());
    }

    @Test
    void its_shadow_can_be_switched_off() {
        settings().set(Settings.FPS_READOUT_ITEM.shadow, false);

        assertFalse(fps().shadow());
    }

    @Test
    void brackets_go_round_the_whole_of_it() {
        settings().set(Settings.FPS_READOUT_ITEM.brackets, true);

        assertEquals("[144 FPS]", fps().text());
    }

    @Test
    void its_label_goes_after_before_or_nowhere() {
        settings().set(Settings.FPS_READOUT_ITEM.label, "before");
        assertEquals("FPS: 144", fps().text());

        settings.set(Settings.FPS_READOUT_ITEM.label, "none");
        assertEquals("144", fps().text());

        settings.set(Settings.PING_READOUT_ITEM.label, "before");
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        items().draw(HudItem.PING, surface);
        assertEquals("Ping: 42 ms", surface.onlyText().text());
    }

    @Test
    void a_release_build_draws_the_readouts_own_look_whatever_the_settings_say() {
        Settings settings = settings();
        settings.set(Settings.FPS_READOUT_ITEM.size, 200);
        settings.set(Settings.FPS_READOUT_ITEM.background, true);
        settings.set(Settings.FPS_READOUT_ITEM.brackets, true);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new HudItems(new HudLayout(settings), new FakeGameView().withFramesPerSecond(144)).draw(surface);

        assertEquals("144 FPS", surface.onlyText().text());
        assertEquals(1f, surface.onlyText().scale());
        assertTrue(surface.fills().isEmpty());
    }

    @Test
    void the_style_is_the_items_options_page_with_no_screen_code_of_its_own() {
        Settings settings = settings();
        SettingsScreen beta = new SettingsScreen(settings, com.ashlauncher.client.report.Target.V1_21_11, true,
                feature -> true, feature -> "", () -> { }, new HudLayout(settings));
        SettingsScreen release = new SettingsScreen(settings, com.ashlauncher.client.report.Target.V1_21_11, false,
                feature -> true, feature -> "", () -> { }, new HudLayout(settings));

        assertEquals(Settings.FPS_READOUT_ITEM.all(), beta.optionsOf(Feature.FPS_READOUT));
        assertEquals(Settings.PING_READOUT_ITEM.all(), beta.optionsOf(Feature.PING_READOUT));
        assertEquals(List.of(), release.optionsOf(Feature.FPS_READOUT));
    }

    @Test
    void an_option_that_would_change_nothing_is_not_offered() {
        Settings settings = settings();

        assertFalse(settings.applies(Settings.FPS_READOUT_ITEM.backgroundColour), "a colour for no box");
        settings.set(Settings.FPS_READOUT_ITEM.background, true);
        assertTrue(settings.applies(Settings.FPS_READOUT_ITEM.backgroundColour));

        settings.set(Settings.FPS_READOUT_ITEM.chroma, true);
        assertFalse(settings.applies(Settings.FPS_READOUT_ITEM.textColour), "a colour chroma draws over");
    }
}
