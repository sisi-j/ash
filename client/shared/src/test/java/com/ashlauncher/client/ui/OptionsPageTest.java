package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Setting;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.draw.FakeCanvas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A feature's options page in ash's panel - the crosshair's, the first -
 * driven as a player would: open it from the card, click and drag its
 * controls, type a colour, reset, go back.
 */
class OptionsPageTest {

    @TempDir
    Path configDir;

    private Settings settings;
    private Panel panel;
    private int width = 1920;
    private int height = 1080;

    private Panel open(int width, int height) {
        return open(Feature.CROSSHAIR, width, height);
    }

    private Panel open(Feature feature, int width, int height) {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, f -> true, () -> { }), () -> { });
        panel.setAnimations(false);
        this.width = width;
        this.height = height;
        panel.resize(width, height);
        render();
        click(panel.optionsLinkOf(feature));
        return panel;
    }

    private Panel open(Feature feature) {
        return open(feature, 1920, 1080);
    }

    private Panel open() {
        return open(1920, 1080);
    }

    private FakeCanvas render() {
        FakeCanvas canvas = new FakeCanvas(width, height);
        panel.render(canvas, -1, -1);
        return canvas;
    }

    private void click(Rect at) {
        assertNotNull(at, "nothing on screen to click");
        panel.mouseClicked(at.centreX(), at.centreY());
        render();
    }

    /** Opens a colour's picker if it is shut, as a player presses its chip before picking. */
    private void picker(com.ashlauncher.client.settings.Colour colour) {
        if (panel.hexBoxOf(colour) == null) {
            click(panel.colourChipOf(colour));
        }
    }

    private Rect swatchIn(com.ashlauncher.client.settings.Colour colour, int rgb) {
        picker(colour);
        return panel.swatchOf(colour, rgb);
    }

    private Rect hexBoxIn(com.ashlauncher.client.settings.Colour colour) {
        picker(colour);
        return panel.hexBoxOf(colour);
    }

    private Rect opacityIn(com.ashlauncher.client.settings.Colour colour, int percent) {
        picker(colour);
        return panel.opacityAt(colour, percent);
    }

    private Rect opacityBarIn(com.ashlauncher.client.settings.Colour colour) {
        picker(colour);
        return panel.opacitySlider(colour);
    }

    private String file() throws IOException {
        return Files.readString(configDir.resolve("ash.properties"));
    }

    @Test
    void a_feature_with_no_options_yet_opens_a_page_that_says_so() throws Exception {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> { });
        panel.setAnimations(false);
        panel.resize(width, height);
        render();

        Rect gear = panel.optionsLinkOf(Feature.FPS_READOUT);
        assertNotNull(gear, "every feature's tile has a gear, as the mockup has it");
        click(gear);
        FakeCanvas page = render();
        page.save(new java.io.File("build/ui/options-none.png"));

        assertTrue(page.drew("FPS readout has no options yet."), "drew " + page.texts());
        assertNotNull(panel.switchOf(Feature.FPS_READOUT), "its ENABLED button is not in the header");
        assertNotNull(panel.backLink());
    }

    @Test
    void the_page_shows_the_feature_its_switch_every_option_a_preview_and_a_way_back() throws Exception {
        open();
        FakeCanvas surface = render();
        surface.save(new java.io.File("build/ui/options-crosshair.png"));

        for (String line : new String[] {"Crosshair", "ENABLED", "Shape", "Size", "Gap", "Thickness", "Colour",
                "Outline", "Reset to defaults", "PREVIEW", "Sky", "Snow", "Night"}) {
            assertTrue(surface.drew(line), "\"" + line + "\" is not on the page: " + surface.texts());
        }
        assertNotNull(panel.switchOf(Feature.CROSSHAIR), "the feature's own switch is not on its page");
        assertNotNull(panel.backLink(), "no way back");
    }

    @Test
    void the_preview_follows_a_change_at_once() {
        open();
        Rect sky = panel.previewScene("Sky");
        assertEquals(0xFFFFFFFF, render().pixel(sky.centreX(), sky.centreY()), "the crosshair's white centre");

        click(swatchIn(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));

        assertEquals(0xFFFF4D4D, render().pixel(sky.centreX(), sky.centreY()), "the preview kept the old colour");
    }

    @Test
    void options_that_do_nothing_for_the_shape_chosen_are_not_on_the_page() {
        open();
        assertNotNull(panel.sliderAt(Settings.CROSSHAIR_GAP, 0));

        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));

        assertNull(panel.sliderAt(Settings.CROSSHAIR_GAP, 0), "a dot has no gap, but the slider is still there");
        assertNull(panel.sliderAt(Settings.CROSSHAIR_THICKNESS, 1), "a dot has no thickness either");
    }

    @Test
    void the_hit_indicators_page_previews_a_hit_and_tests_one_on_demand() throws Exception {
        open(Feature.HIT_INDICATOR);
        FakeCanvas surface = render();
        surface.save(new java.io.File("build/ui/options-hit-indicator.png"));

        assertTrue(surface.drew("Test a hit"), "drew " + surface.texts());
        assertTrue(surface.drew("Duration"));
        assertNotNull(panel.testHitButton());
    }

    @Test
    void choosing_a_shape_changes_it_now_and_in_the_file() throws IOException {
        open();

        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));

        assertEquals("dot", settings.get(Settings.CROSSHAIR_SHAPE));
        assertTrue(file().contains("\ncrosshair.shape=dot\n"), file());
    }

    @Test
    void clicking_along_a_slider_sets_the_value_under_the_mouse_and_dragging_follows_it() throws IOException {
        open();

        click(panel.sliderAt(Settings.CROSSHAIR_SIZE, 8));
        assertEquals(8, settings.get(Settings.CROSSHAIR_SIZE), "a click on the track");
        assertTrue(file().contains("\ncrosshair.size=8\n"), file());

        Rect two = panel.sliderAt(Settings.CROSSHAIR_SIZE, 2);
        panel.mouseDragged(two.centreX(), two.centreY());
        assertEquals(2, settings.get(Settings.CROSSHAIR_SIZE), "a drag after the click");

        panel.mouseReleased();
        Rect ten = panel.sliderAt(Settings.CROSSHAIR_SIZE, 10);
        panel.mouseDragged(ten.centreX(), ten.centreY());
        assertEquals(2, settings.get(Settings.CROSSHAIR_SIZE), "a drag after the mouse was released");
    }

    @Test
    void a_slider_never_goes_past_its_ends_however_far_it_is_dragged() {
        open();
        click(panel.sliderAt(Settings.CROSSHAIR_GAP, 1));

        panel.mouseDragged(-500, 0);
        assertEquals(0, settings.get(Settings.CROSSHAIR_GAP));
        panel.mouseDragged(5000, 0);
        assertEquals(5, settings.get(Settings.CROSSHAIR_GAP));
    }

    @Test
    void a_swatch_sets_the_colour_and_keeps_its_opacity() throws IOException {
        open();
        settings.set(Settings.CROSSHAIR_COLOUR, 0x80FFFFFF);

        click(swatchIn(Settings.CROSSHAIR_COLOUR, 0x4DC3FF));

        assertEquals(0x804DC3FF, (int) settings.get(Settings.CROSSHAIR_COLOUR));
        assertTrue(file().contains("\ncrosshair.colour=#4DC3FF80\n"), file());
    }

    @Test
    void the_opacity_slider_never_goes_below_the_faintest_both_targets_draw_alike() {
        open();
        Rect opacity = opacityBarIn(Settings.CROSSHAIR_COLOUR);
        click(opacity);

        panel.mouseDragged(opacity.centreX(), 10_000);

        assertTrue((settings.get(Settings.CROSSHAIR_COLOUR) >>> 24) >= com.ashlauncher.client.settings.Colour.MIN_ALPHA,
                Integer.toHexString(settings.get(Settings.CROSSHAIR_COLOUR)));
    }

    @Test
    void a_colour_typed_into_its_box_applies_as_soon_as_it_is_whole_and_half_of_one_does_not() {
        open();

        click(hexBoxIn(Settings.CROSSHAIR_COLOUR));
        for (int i = 0; i < 7; i++) {
            panel.keyPressed(Key.BACKSPACE);
        }
        for (char c : "ff4d4d".toCharArray()) {
            panel.charTyped(c);
        }
        panel.keyPressed(Key.ENTER);
        assertEquals(0xFFFF4D4D, (int) settings.get(Settings.CROSSHAIR_COLOUR));

        click(hexBoxIn(Settings.CROSSHAIR_COLOUR));
        panel.charTyped('z');
        panel.keyPressed(Key.BACKSPACE);
        panel.keyPressed(Key.BACKSPACE);
        panel.keyPressed(Key.ENTER);
        assertEquals(0xFFFF4D4D, (int) settings.get(Settings.CROSSHAIR_COLOUR), "a half-typed colour was applied");
    }

    @Test
    void escape_while_typing_a_colour_stops_typing_and_does_not_close_the_panel() {
        java.util.List<String> closed = new java.util.ArrayList<>();
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> closed.add("closed"));
        panel.setAnimations(false);
        panel.resize(width, height);
        render();
        click(panel.optionsLinkOf(Feature.CROSSHAIR));
        click(hexBoxIn(Settings.CROSSHAIR_COLOUR));

        panel.keyPressed(Key.ESCAPE);
        assertEquals(java.util.List.of(), closed, "Escape closed the panel mid-typing");
        render();
        assertNotNull(panel.backLink(), "the first Escape left the page instead of just stopping the typing");
        panel.keyPressed(Key.ESCAPE);
        assertEquals(java.util.List.of(), closed, "the second Escape closed the panel instead of going back");
        panel.keyPressed(Key.ESCAPE);
        assertEquals(java.util.List.of("closed"), closed, "a third Escape, on the tiles, did not close it");
    }

    @Test
    void an_option_that_is_on_or_off_has_a_switch() {
        open();

        click(panel.switchOf(Settings.CROSSHAIR_OUTLINE));

        assertFalse(settings.get(Settings.CROSSHAIR_OUTLINE));
    }

    @Test
    void reset_puts_every_option_and_the_switch_back_to_their_defaults() {
        open();
        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "box"));
        click(panel.sliderAt(Settings.CROSSHAIR_THICKNESS, 3));
        click(swatchIn(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));
        click(panel.switchOf(Feature.CROSSHAIR));

        click(panel.resetToDefaults());

        for (Setting<?> option : Settings.optionsOf(Feature.CROSSHAIR)) {
            assertEquals(option.fallback(), settings.get(option), option.key() + " was not reset");
        }
        assertTrue(settings.get(Settings.CROSSHAIR), "the crosshair's own switch was not put back to its default");
    }

    @Test
    void the_preview_shows_the_crosshair_as_set_over_sky_snow_and_night() {
        open();
        click(swatchIn(Settings.CROSSHAIR_COLOUR, 0x4DFF88));

        FakeCanvas surface = render();

        for (String name : new String[] {"Sky", "Snow", "Night"}) {
            assertTrue(surface.drew(name), name + " is not labelled");
        }
        long green = surface.fills.stream().filter(f -> f.argb() == 0xFF4DFF88).count();
        assertTrue(green >= 3, "the chosen colour is not in all three previews: " + green + " fills");
    }

    @Test
    void back_returns_to_the_cards() {
        open();

        click(panel.backLink());

        assertNotNull(panel.switchOf(Feature.FPS_READOUT), "the cards did not come back");
        assertNull(panel.backLink(), "the back link is still there");
    }

    @Test
    void at_the_smallest_gui_size_every_control_can_be_reached() {
        open(854, 480);
        FakeCanvas surface = render();

        for (FakeCanvas.Drawn text : surface.drawn) {
            if (text.text() != null) {
                assertTrue(text.x() >= 0 && text.x() + text.width() <= 854 && text.y() + text.height() <= 480,
                        "off screen: " + text.text());
            }
        }
        for (int i = 0; i < 20 && panel.resetToDefaults() == null; i++) {
            panel.mouseScrolled(-1);
            render();
        }
        assertNotNull(panel.resetToDefaults(), "Reset to defaults cannot be reached, even by scrolling");
    }

    @Test
    void the_opacity_slider_sets_the_opacity_under_the_mouse() {
        open();

        click(opacityIn(Settings.CROSSHAIR_COLOUR, 50));

        assertEquals(0x80, settings.get(Settings.CROSSHAIR_COLOUR) >>> 24, "50% is not half opaque");
    }

    @Test
    void pressing_a_slider_s_handle_where_it_stands_leaves_it_there() {
        // The opacity slider has nearly a hundred steps on a short track, so
        // a press one unit off the handle's middle would move it.
        open();
        settings.set(Settings.CROSSHAIR_COLOUR, 0x80FFFFFF);
        render();

        click(opacityIn(Settings.CROSSHAIR_COLOUR, 50));

        assertEquals(0x80FFFFFF, (int) settings.get(Settings.CROSSHAIR_COLOUR));
    }

    @Test
    void a_dot_is_not_offered_a_gap_or_a_thickness_it_would_ignore() {
        open();

        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));
        FakeCanvas surface = render();

        assertFalse(surface.drew("Gap"), "a dot is offered a gap");
        assertFalse(surface.drew("Thickness"), "a dot is offered a thickness");
        assertTrue(surface.drew("Size"), "a dot is not offered a size");
        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "cross"));
        assertTrue(render().drew("Gap"), "a cross is not offered a gap");
    }

    @Test
    void a_crosshair_that_did_not_load_offers_no_options() {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> feature != Feature.CROSSHAIR, () -> { }),
                () -> { });
        panel.setAnimations(false);
        panel.resize(width, height);
        render();

        assertNull(panel.optionsLinkOf(Feature.CROSSHAIR), "options for a crosshair the game is not drawing");
    }

    @Test
    void the_hit_indicator_s_page_sets_its_colour_and_how_long_it_shows_in_steps_of_50_ms() throws IOException {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> { });
        panel.setAnimations(false);
        panel.resize(width, height);
        render();
        click(panel.optionsLinkOf(Feature.HIT_INDICATOR));
        FakeCanvas surface = render();

        for (String line : new String[] {"Hit indicator", "Colour", "#FF4D4D · 100%", "Duration", "300 ms"}) {
            assertTrue(surface.drew(line), "\"" + line + "\" is not on the page: " + surface.texts());
        }
        assertFalse(surface.drew("Preview"), "a preview of a crosshair on the hit indicator's page");

        for (int wanted = 100; wanted <= 1000; wanted += 50) {
            click(panel.sliderAt(Settings.HIT_INDICATOR_DURATION, wanted));
            assertEquals(wanted, settings.get(Settings.HIT_INDICATOR_DURATION), "aiming at " + wanted);
        }
        click(swatchIn(Settings.HIT_INDICATOR_COLOUR, 0x4DFF88));
        assertTrue(file().contains("\nhit-indicator.colour=#4DFF88FF\n"), file());
        assertTrue(file().contains("\nhit-indicator.duration=1000\n"), file());
    }
}
