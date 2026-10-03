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
        return render(panel, new FakeScreenSurface());
    }

    private static FakeScreenSurface render(Panel panel, FakeScreenSurface surface) {
        panel.render(surface, -1, -1);
        return surface;
    }

    /** The text drawn inside a rectangle, top to bottom, joined with spaces. */
    private static String textIn(FakeScreenSurface surface, Rect area) {
        StringBuilder text = new StringBuilder();
        for (FakeScreenSurface.Text drawn : surface.texts) {
            if (area.contains(drawn.x(), drawn.y())) {
                text.append(text.length() == 0 ? "" : " ").append(drawn.text());
            }
        }
        return text.toString();
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
        FakeScreenSurface surface;
        assertFalse(Settings.declared().isEmpty(), "the test proves nothing with no settings declared");
        Panel panel = panel(Settings.load(configDir));
        surface = render(panel);
        for (OnOff setting : Settings.declared()) {
            Rect card = panel.cardOf(setting.feature());
            String inCard = textIn(surface, card);
            // The name, then as much of its own description as fits, in its own card.
            String name = setting.feature().displayName();
            assertTrue(inCard.startsWith(name + " "), setting.key() + "'s card reads \"" + inCard + "\"");
            String shown = inCard.substring(name.length() + 1).replace("...", "");
            assertTrue(shown.length() >= 12 && setting.description().startsWith(shown),
                    setting.key() + "'s card shows \"" + shown + "\", not its description");
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
        Panel panel = panel(Settings.load(configDir));
        FakeScreenSurface surface = render(panel);

        for (String category : new String[] {"All", "PvP", "HUD", "Movement"}) {
            long expected = category.equals("All") ? Settings.declared().size()
                    : Settings.declared().stream().filter(s -> s.category().displayName().equals(category)).count();
            assertEquals(category + " " + expected, textIn(surface, panel.categoryAt(category)),
                    "the " + category + " row");
        }
    }

    @Test
    void no_category_s_name_runs_into_its_count() {
        // The game's own font drew "Movement1" in the first real-game
        // screenshot: the column was sized by guess, not by the font.
        Panel panel = panel(Settings.load(configDir));
        FakeScreenSurface surface = render(panel);

        for (String category : new String[] {"All", "PvP", "HUD", "Movement"}) {
            Rect row = panel.categoryAt(category);
            FakeScreenSurface.Text name = null;
            FakeScreenSurface.Text count = null;
            for (FakeScreenSurface.Text text : surface.texts) {
                if (row.contains(text.x(), text.y())) {
                    if (name == null) {
                        name = text;
                    } else {
                        count = text;
                    }
                }
            }
            assertTrue(name != null && count != null, category + " is not drawn as a name and a count");
            int space = count.x() - (name.x() + surface.textWidth(name.text()));
            assertTrue(space >= 4, category + "'s name and count are " + space + " apart");
            assertTrue(count.x() + surface.textWidth(count.text()) <= row.x + row.width, category + "'s count overflows");
        }
    }

    @Test
    void a_switch_is_drawn_as_what_it_is_knob_right_when_on_and_left_when_off() {
        Settings settings = Settings.load(configDir);
        Panel panel = panel(settings);
        Rect on = panel.switchOf(Feature.FPS_READOUT);

        assertEquals(Palette.BACKGROUND, knobColour(render(panel), on, true), "an on switch's knob is not at the right");
        click(panel, on);
        assertEquals(Palette.MUTED, knobColour(render(panel), on, false), "an off switch's knob is not at the left");
    }

    /** The colour filled at the middle of one end of a switch, where its knob sits. */
    private static int knobColour(FakeScreenSurface surface, Rect at, boolean right) {
        int x = right ? at.x + at.width - 5 : at.x + 4;
        int y = at.centreY();
        int colour = 0;
        for (FakeScreenSurface.Fill fill : surface.fills) {
            if (x >= fill.x() && x < fill.x() + fill.width() && y >= fill.y() && y < fill.y() + fill.height()) {
                colour = fill.colour();
            }
        }
        return colour;
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
            panel.keyPressed(Key.BACKSPACE);
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
    void escape_closes_the_panel() {
        Panel panel = panel(Settings.load(configDir));

        panel.keyPressed(Key.ESCAPE);

        assertEquals(List.of("closed"), closed);
    }

    @Test
    void typing_never_splits_a_character_in_two() {
        // A character outside the basic plane is two chars in Java; the
        // search keeps it whole when typed and when deleted.
        Panel panel = panel(Settings.load(configDir));
        int grin = 0x1F600;

        panel.charTyped('a');
        panel.charTyped(grin);
        panel.keyPressed(Key.BACKSPACE);

        assertTrue(render(panel).drew("a"), "Backspace took half a character, or the 'a' with it");
    }

    @Test
    void cards_that_do_not_fit_are_reached_by_scrolling() {
        // A taller font - as ash's own typeface might be - makes the cards
        // taller, so three no longer fit at 320 by 240, the smallest GUI size
        // the game ever uses.
        Settings settings = Settings.load(configDir);
        Panel panel = panel(settings);
        panel.resize(320, 240);
        FakeScreenSurface tall = render(panel, new FakeScreenSurface(18));
        assertTrue(panel.maxScroll() > 0, "the test proves nothing if every card fits");
        assertEquals(null, panel.switchOf(Feature.CROSSHAIR), "the third card is on view without scrolling");
        assertTrue(tall.fills.stream().anyMatch(f -> f.colour() == Palette.MUTED && f.width() == 2),
                "nothing shows there is more to scroll to");

        panel.mouseScrolled(-1);
        render(panel, new FakeScreenSurface(18));
        click(panel, panel.switchOf(Feature.CROSSHAIR));

        assertFalse(settings.get(Settings.CROSSHAIR), "the card scrolled to could not be switched");
        panel.mouseScrolled(1);
        render(panel, new FakeScreenSurface(18));
        assertTrue(panel.switchOf(Feature.FPS_READOUT) != null, "scrolling back up did not bring the first card back");
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
    void at_every_common_gui_size_everything_stays_on_screen_and_no_card_overlaps_another() {
        // The game picks the largest GUI scale that leaves at least 320 by 240,
        // so 320 by 240 is the smallest a GUI ever is; 427 by 240 is 1280 by
        // 720 at scale 3. Each size is tried with and without the notice a
        // feature that did not load puts above the footer.
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
          for (boolean notice : new boolean[] {false, true}) {
            Panel panel = panel(Settings.load(configDir), feature -> !notice || feature != Feature.TOGGLE_SPRINT);
            panel.resize(size[0], size[1]);
            FakeScreenSurface surface = render(panel);
            String at = size[0] + "x" + size[1] + (notice ? " with the notice" : "");

            for (FakeScreenSurface.Fill fill : surface.fills) {
                assertTrue(fill.x() >= 0 && fill.y() >= 0 && fill.x() + fill.width() <= size[0]
                        && fill.y() + fill.height() <= size[1], "off screen at " + at + ": " + fill);
            }
            for (FakeScreenSurface.Text text : surface.texts) {
                assertTrue(text.x() >= 0 && text.y() >= 0 && text.x() + surface.textWidth(text.text()) <= size[0]
                        && text.y() + surface.lineHeight() <= size[1], "text off screen at " + at + ": " + text);
            }
            for (OnOff setting : Settings.declared()) {
                assertTrue(panel.cardOf(setting.feature()) != null || panel.maxScroll() > 0,
                        setting.key() + "'s card is neither on view nor reachable by scrolling at " + at);
            }
            List<Rect> cards = new ArrayList<>();
            for (OnOff setting : Settings.declared()) {
                Rect card = panel.cardOf(setting.feature());
                if (card != null) {
                    cards.add(card);
                }
            }
            for (int i = 0; i < cards.size(); i++) {
                for (int j = i + 1; j < cards.size(); j++) {
                    assertFalse(cards.get(i).overlaps(cards.get(j)), "cards overlap at " + at + ": " + cards.get(i) + " "
                            + cards.get(j));
                }
            }
          }
        }
    }
}
