package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.draw.FakeCanvas;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The colour picker (#68), driven as a player drives it: its chip, then presses and drags across its parts. */
class ColourPickerTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @TempDir
    Path configDir;

    private Settings settings;
    private Panel panel;

    private void open(Feature feature) {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, f -> true, () -> { }), () -> { });
        panel.setAnimations(false);
        panel.resize(WIDTH, HEIGHT);
        render();
        click(panel.optionsLinkOf(feature));
    }

    private FakeCanvas render() {
        FakeCanvas canvas = new FakeCanvas(WIDTH, HEIGHT);
        panel.render(canvas, -1, -1);
        return canvas;
    }

    private void click(Rect at) {
        assertNotNull(at, "nothing on screen to click");
        panel.mouseClicked(at.centreX(), at.centreY());
        panel.mouseReleased();
        render();
    }

    /** A press, a drag to a point, and a release, as a player drags. */
    private void drag(Rect from, Rect to) {
        assertNotNull(from, "nothing to drag from");
        assertNotNull(to, "nothing to drag to");
        panel.mouseClicked(from.centreX(), from.centreY());
        panel.mouseDragged(to.centreX(), to.centreY());
        panel.mouseReleased();
        render();
    }

    private String file() throws Exception {
        return Files.readString(configDir.resolve("ash.properties"));
    }

    @Test
    void the_chip_folds_the_picker_open_and_shut() throws Exception {
        open(Feature.CROSSHAIR);
        assertNull(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR), "the picker starts open");

        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        FakeCanvas canvas = render();
        canvas.save(new File("build/ui/options-picker.png"));
        assertNotNull(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR));
        assertNotNull(panel.opacitySlider(Settings.CROSSHAIR_COLOUR));
        assertTrue(canvas.drew("PRESETS") && canvas.drew("RECENT"), "drew " + canvas.texts());
        assertTrue(canvas.drew("Colours you use show here"), "no word for an empty recent row");

        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        assertNull(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR), "the chip did not fold it shut");
    }

    @Test
    void the_square_sets_saturation_and_brightness_and_the_file_and_preview_follow() throws Exception {
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        Colour colour = Settings.CROSSHAIR_COLOUR;

        // White is hue 0 at no saturation: full saturation at full brightness is red.
        drag(panel.colourSquareAt(colour, 0.5f, 0.5f), panel.colourSquareAt(colour, 1f, 1f));

        assertEquals(0xFFFF0000, (int) settings.get(colour));
        assertTrue(file().contains("\ncrosshair.colour=#FF0000FF\n"), file());
        Rect sky = panel.previewScene("Sky");
        assertEquals(0xFFFF0000, render().pixel(sky.centreX(), sky.centreY()), "the preview did not follow");
    }

    @Test
    void the_hue_bar_turns_the_colour_round_the_wheel() {
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        Colour colour = Settings.CROSSHAIR_COLOUR;
        drag(panel.colourSquareAt(colour, 0.5f, 0.5f), panel.colourSquareAt(colour, 1f, 1f));

        click(panel.hueAt(colour, 120));

        int rgb = settings.get(colour) & 0xFFFFFF;
        assertTrue(((rgb >> 8) & 0xFF) > 0xF0 && ((rgb >> 16) & 0xFF) < 0x10, "a third of the way round is green, not "
                + Integer.toHexString(rgb));
    }

    @Test
    void dragging_through_grey_keeps_the_hue_it_had() {
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        Colour colour = Settings.CROSSHAIR_COLOUR;
        drag(panel.colourSquareAt(colour, 0.5f, 0.5f), panel.colourSquareAt(colour, 1f, 1f));
        click(panel.hueAt(colour, 240));

        // Out to grey, where there is no hue, and back to full colour.
        drag(panel.colourSquareAt(colour, 1f, 1f), panel.colourSquareAt(colour, 0f, 1f));
        drag(panel.colourSquareAt(colour, 0f, 1f), panel.colourSquareAt(colour, 1f, 1f));

        int rgb = settings.get(colour) & 0xFFFFFF;
        assertTrue((rgb & 0xFF) > 0xF0 && (rgb >> 16) < 0x10, "grey forgot the blue it came from: "
                + Integer.toHexString(rgb));
    }

    @Test
    void the_opacity_bar_sets_opacity_but_never_below_the_faintest_both_targets_draw_alike() {
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        Colour colour = Settings.CROSSHAIR_COLOUR;

        click(panel.opacityAt(colour, 50));
        assertEquals(0x80, settings.get(colour) >>> 24, "half way down is half opaque");

        Rect bar = panel.opacitySlider(colour);
        drag(bar, new Rect(bar.x, bar.y + bar.height * 4, 1, 1));
        assertEquals(Colour.MIN_ALPHA, settings.get(colour) >>> 24);
    }

    @Test
    void a_colour_without_opacity_has_no_opacity_bar() {
        open(Feature.HIT_COLOUR);

        click(panel.colourChipOf(Settings.HIT_COLOUR_COLOUR));

        assertNotNull(panel.hexBoxOf(Settings.HIT_COLOUR_COLOUR));
        assertNull(panel.opacitySlider(Settings.HIT_COLOUR_COLOUR), "an opaque colour was offered an opacity");
    }

    @Test
    void colours_picked_are_remembered_newest_first_across_sessions() {
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        click(panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0x4DC3FF));
        click(panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));
        assertNotNull(panel.recentOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));

        // A new session, as the next time the game starts.
        open(Feature.CROSSHAIR);
        click(panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        Rect newest = panel.recentOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D);
        Rect older = panel.recentOf(Settings.CROSSHAIR_COLOUR, 0x4DC3FF);
        assertNotNull(newest, "the recent colours did not survive the session");
        assertNotNull(older);
        assertTrue(newest.x < older.x, "not newest first");

        click(older);
        assertEquals(0xFF4DC3FF, (int) settings.get(Settings.CROSSHAIR_COLOUR), "a recent colour does not set it");
    }

    @Test
    void the_recent_row_keeps_six_and_no_twins() {
        RecentColours recent = RecentColours.load(configDir);
        for (int i = 0; i < 8; i++) {
            recent.remember(0x100000 * i);
        }
        recent.remember(0x700000);

        assertEquals(RecentColours.KEPT, recent.colours().size());
        assertEquals(0x700000, (int) recent.colours().get(0));
        assertEquals(java.util.List.of(0x700000, 0x600000, 0x500000, 0x400000, 0x300000, 0x200000),
                RecentColours.load(configDir).colours());
    }
}
