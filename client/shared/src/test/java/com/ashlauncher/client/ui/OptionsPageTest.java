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

    private Panel open(int width, int height) {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> "Right Shift", () -> { });
        panel.resize(width, height);
        render();
        click(panel.optionsLinkOf(Feature.CROSSHAIR));
        return panel;
    }

    private Panel open() {
        return open(427, 240);
    }

    private FakeScreenSurface render() {
        FakeScreenSurface surface = new FakeScreenSurface();
        panel.render(surface, -1, -1);
        return surface;
    }

    private void click(Rect at) {
        assertNotNull(at, "nothing on screen to click");
        panel.mouseClicked(at.centreX(), at.centreY());
        render();
    }

    private String file() throws IOException {
        return Files.readString(configDir.resolve("ash.properties"));
    }

    @Test
    void only_a_feature_with_options_has_an_options_link() {
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> "Right Shift", () -> { });
        panel.resize(427, 240);
        render();

        assertNotNull(panel.optionsLinkOf(Feature.CROSSHAIR), "the crosshair's card has no options link");
        assertNull(panel.optionsLinkOf(Feature.FPS_READOUT), "a feature with no options has an options link");
    }

    @Test
    void the_page_shows_the_feature_its_switch_every_option_and_a_way_back() {
        open();
        FakeScreenSurface surface = render();

        for (String line : new String[] {"< All features", "Crosshair", "Shape", "Size", "Gap", "Thickness", "Colour",
                "Opacity", "Outline", "Reset to defaults", "Preview"}) {
            assertTrue(surface.drew(line), "\"" + line + "\" is not on the page: " + surface.lines());
        }
        assertNotNull(panel.switchOf(Feature.CROSSHAIR), "the feature's own switch is not on its page");
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

        click(panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0x4DC3FF));

        assertEquals(0x804DC3FF, (int) settings.get(Settings.CROSSHAIR_COLOUR));
        assertTrue(file().contains("\ncrosshair.colour=#4DC3FF80\n"), file());
    }

    @Test
    void the_opacity_slider_never_goes_below_the_faintest_both_targets_draw_alike() {
        open();
        Rect opacity = panel.opacitySlider(Settings.CROSSHAIR_COLOUR);
        click(opacity);

        panel.mouseDragged(-500, opacity.centreY());

        assertTrue((settings.get(Settings.CROSSHAIR_COLOUR) >>> 24) >= com.ashlauncher.client.settings.Colour.MIN_ALPHA,
                Integer.toHexString(settings.get(Settings.CROSSHAIR_COLOUR)));
    }

    @Test
    void a_colour_typed_into_its_box_applies_as_soon_as_it_is_whole_and_half_of_one_does_not() {
        open();

        click(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR));
        for (int i = 0; i < 7; i++) {
            panel.keyPressed(Key.BACKSPACE);
        }
        for (char c : "ff4d4d".toCharArray()) {
            panel.charTyped(c);
        }
        panel.keyPressed(Key.ENTER);
        assertEquals(0xFFFF4D4D, (int) settings.get(Settings.CROSSHAIR_COLOUR));

        click(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR));
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
        panel = new Panel(new SettingsScreen(settings, feature -> true, () -> { }), () -> "Right Shift",
                () -> closed.add("closed"));
        panel.resize(427, 240);
        render();
        click(panel.optionsLinkOf(Feature.CROSSHAIR));
        click(panel.hexBoxOf(Settings.CROSSHAIR_COLOUR));

        panel.keyPressed(Key.ESCAPE);
        assertEquals(java.util.List.of(), closed, "Escape closed the panel mid-typing");
        panel.keyPressed(Key.ESCAPE);
        assertEquals(java.util.List.of("closed"), closed, "a second Escape did not close it");
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
        click(panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));
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
        click(panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0x4DFF88));

        FakeScreenSurface surface = render();

        for (String name : new String[] {"Sky", "Snow", "Night"}) {
            assertTrue(surface.drew(name), name + " is not labelled");
        }
        long green = surface.fills.stream().filter(f -> f.colour() == 0xFF4DFF88).count();
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
        open(320, 240);
        FakeScreenSurface surface = render();

        for (FakeScreenSurface.Text text : surface.texts) {
            assertTrue(text.x() >= 0 && text.x() + surface.textWidth(text.text()) <= 320 && text.y() + 9 <= 240,
                    "off screen: " + text);
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

        click(panel.opacityAt(Settings.CROSSHAIR_COLOUR, 50));

        assertEquals(0x80, settings.get(Settings.CROSSHAIR_COLOUR) >>> 24, "50% is not half opaque");
    }

    @Test
    void pressing_a_slider_s_handle_where_it_stands_leaves_it_there() {
        // The opacity slider has nearly a hundred steps on a short track, so
        // a press one unit off the handle's middle would move it.
        open();
        settings.set(Settings.CROSSHAIR_COLOUR, 0x80FFFFFF);
        render();

        click(panel.opacityAt(Settings.CROSSHAIR_COLOUR, 50));

        assertEquals(0x80FFFFFF, (int) settings.get(Settings.CROSSHAIR_COLOUR));
    }

    @Test
    void a_dot_is_not_offered_a_gap_or_a_thickness_it_would_ignore() {
        open();

        click(panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));
        FakeScreenSurface surface = render();

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
                () -> "Right Shift", () -> { });
        panel.resize(427, 240);
        render();

        assertNull(panel.optionsLinkOf(Feature.CROSSHAIR), "options for a crosshair the game is not drawing");
    }
}
