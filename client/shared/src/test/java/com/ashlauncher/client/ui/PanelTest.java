package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ash's settings panel, driven the way a player drives it - clicks at points
 * on the screen, keys typed - and judged by what it draws and what it saves.
 * No game: the surface is a fake that records text and rectangles.
 */
class PanelTest {

    /** The GUI size of a 1280 by 720 window at the default scale. */
    private static final int WIDTH = 427;
    private static final int HEIGHT = 240;

    @TempDir
    Path configDir;

    private final List<String> closed = new ArrayList<>();

    private Panel panel(Settings settings, Predicate<Feature> landed) {
        Panel panel = new Panel(new SettingsScreen(settings, landed, () -> { }), () -> "Right Shift",
                () -> closed.add("closed"));
        panel.resize(WIDTH, HEIGHT);
        return panel;
    }

    private Panel panel(Settings settings) {
        return panel(settings, feature -> true);
    }

    private static FakeScreenSurface render(Panel panel) {
        FakeScreenSurface surface = new FakeScreenSurface();
        panel.render(surface, -1, -1);
        return surface;
    }

    private static void click(Panel panel, Rect at) {
        panel.mouseClicked(at.centreX(), at.centreY());
    }

    private static void type(Panel panel, String text) {
        for (char c : text.toCharArray()) {
            panel.charTyped(c);
        }
    }

    @Test
    void every_declared_setting_has_a_card_with_its_name_and_what_it_does() {
        FakeScreenSurface surface = render(panel(Settings.load(configDir)));

        assertFalse(Settings.declared().isEmpty(), "the test proves nothing with no settings declared");
        for (OnOff setting : Settings.declared()) {
            assertTrue(surface.drew(setting.feature().displayName()), setting.key() + " has no card: " + surface.lines());
            assertTrue(String.join(" ", surface.lines()).contains(setting.feature().description().split(" ")[0]),
                    setting.key() + "'s description was not drawn");
        }
        assertTrue(surface.drew("ash"), "no wordmark");
    }

    @Test
    void clicking_a_card_s_switch_switches_the_feature_now_and_in_the_file() {
        Settings settings = Settings.load(configDir);
        Panel panel = panel(settings);
        render(panel);

        click(panel, panel.switchOf(Feature.FPS_READOUT));

        assertFalse(settings.get(Settings.FPS_READOUT), "the running game did not see the change");
        assertFalse(Settings.load(configDir).get(Settings.FPS_READOUT), "the change was not saved");
        click(panel, panel.switchOf(Feature.FPS_READOUT));
        assertTrue(settings.get(Settings.FPS_READOUT), "a second click did not switch it back");
    }

    @Test
    void a_category_shows_only_its_own_cards_and_all_shows_every_one() {
        Panel panel = panel(Settings.load(configDir));
        render(panel);

        click(panel, panel.categoryAt("Movement"));
        FakeScreenSurface movement = render(panel);

        assertTrue(movement.drew("Toggle sprint"));
        assertFalse(movement.drew("FPS readout"), "a HUD card shown under Movement");
        assertFalse(movement.drew("Crosshair"), "a PvP card shown under Movement");

        click(panel, panel.categoryAt("All"));
        FakeScreenSurface all = render(panel);
        for (OnOff setting : Settings.declared()) {
            assertTrue(all.drew(setting.feature().displayName()), setting.key() + " missing under All");
        }
    }

    @Test
    void each_category_is_listed_with_how_many_features_it_has() {
        FakeScreenSurface surface = render(panel(Settings.load(configDir)));

        for (String category : new String[] {"All", "PvP", "HUD", "Movement"}) {
            assertTrue(surface.drew(category), category + " is not listed");
        }
        assertTrue(surface.drew(String.valueOf(Settings.declared().size())), "All's count is not shown");
    }

    @Test
    void typing_filters_the_cards_and_backspace_brings_them_back() {
        Panel panel = panel(Settings.load(configDir));

        type(panel, "cross");
        FakeScreenSurface filtered = render(panel);

        assertTrue(filtered.drew("cross"), "what was typed is not shown in the search box");
        assertTrue(filtered.drew("Crosshair"));
        assertFalse(filtered.drew("FPS readout"), "a card that does not match is still shown");

        for (int i = 0; i < "cross".length(); i++) {
            panel.backspace();
        }
        FakeScreenSurface cleared = render(panel);
        assertTrue(cleared.drew("FPS readout"), "clearing the search did not bring the cards back");
        assertTrue(cleared.drew("Search features"), "the empty search box lost its hint");
    }

    @Test
    void a_search_that_matches_nothing_says_so() {
        Panel panel = panel(Settings.load(configDir));

        type(panel, "reach");
        FakeScreenSurface surface = render(panel);

        assertTrue(surface.drew("Nothing matches \"reach\"."), surface.lines().toString());
    }

    @Test
    void a_feature_that_did_not_load_says_so_and_its_switch_does_nothing() {
        Settings settings = Settings.load(configDir);
        Panel panel = panel(settings, feature -> feature != Feature.TOGGLE_SPRINT);
        FakeScreenSurface surface = render(panel);

        click(panel, panel.switchOf(Feature.TOGGLE_SPRINT));

        assertTrue(surface.drew("Did not load"), surface.lines().toString());
        assertTrue(settings.get(Settings.TOGGLE_SPRINT), "a switch that does nothing was switched anyway");
        assertTrue(String.join(" ", surface.lines()).contains("problem with ash"),
                "the footer does not say whose problem it is: " + surface.lines());
    }

    @Test
    void the_close_button_closes_the_panel_and_the_footer_names_the_key() {
        Panel panel = panel(Settings.load(configDir));
        FakeScreenSurface surface = render(panel);

        click(panel, panel.closeButton());

        assertEquals(List.of("closed"), closed);
        assertTrue(surface.drew("Right Shift closes"), surface.lines().toString());
        assertTrue(surface.drew("Changes save as you make them"), surface.lines().toString());
    }

    @Test
    void a_click_on_nothing_does_nothing() {
        Settings settings = Settings.load(configDir);
        Panel panel = panel(settings);
        render(panel);

        boolean handled = panel.mouseClicked(1, 1);

        assertFalse(handled);
        assertEquals(List.of(), closed);
        for (OnOff setting : Settings.declared()) {
            assertEquals(setting.fallback(), settings.get(setting), setting.key());
        }
    }

    @Test
    void at_the_smallest_common_gui_size_everything_stays_on_screen_and_no_card_overlaps_another() {
        // 1280 by 720 at GUI scale 4 is 320 by 180: the tightest a common
        // window gets before the game drops to a smaller scale.
        for (int[] size : new int[][] {{320, 180}, {427, 240}, {640, 360}, {960, 540}}) {
            Panel panel = panel(Settings.load(configDir));
            panel.resize(size[0], size[1]);
            FakeScreenSurface surface = render(panel);

            for (FakeScreenSurface.Fill fill : surface.fills) {
                assertTrue(fill.x() >= 0 && fill.y() >= 0 && fill.x() + fill.width() <= size[0]
                        && fill.y() + fill.height() <= size[1], "off screen at " + size[0] + "x" + size[1] + ": " + fill);
            }
            List<Rect> switches = new ArrayList<>();
            for (OnOff setting : Settings.declared()) {
                switches.add(panel.cardOf(setting.feature()));
            }
            for (int i = 0; i < switches.size(); i++) {
                for (int j = i + 1; j < switches.size(); j++) {
                    assertFalse(switches.get(i).overlaps(switches.get(j)),
                            "cards overlap at " + size[0] + "x" + size[1] + ": " + switches.get(i) + " " + switches.get(j));
                }
            }
        }
    }
}
