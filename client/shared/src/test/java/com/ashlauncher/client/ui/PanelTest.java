package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.draw.FakeCanvas;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ash's settings panel in the final design, driven the way a player drives
 * it - clicks at points on the screen, keys - and judged by what it draws and
 * what it saves. No game: the canvas is a fake that records what was drawn
 * and composites real pixels.
 */
class PanelTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @TempDir
    Path configDir;

    private final List<String> closed = new ArrayList<>();
    private final AtomicLong now = new AtomicLong();
    private Settings settings;

    private Panel panel(Predicate<Feature> landed, int width, int height) {
        settings = Settings.load(configDir);
        Panel panel = new Panel(new SettingsScreen(settings, landed, () -> { }), () -> closed.add("closed"), now::get);
        panel.resize(width, height);
        return panel;
    }

    private Panel panel() {
        return panel(feature -> true, WIDTH, HEIGHT);
    }

    private static FakeCanvas render(Panel panel) {
        return render(panel, WIDTH, HEIGHT);
    }

    private static FakeCanvas render(Panel panel, int width, int height) {
        FakeCanvas canvas = new FakeCanvas(width, height);
        panel.render(canvas, -1, -1);
        return canvas;
    }

    private static void click(Panel panel, Rect at) {
        assertNotNull(at, "nothing on screen to click");
        assertTrue(panel.mouseClicked(at.centreX(), at.centreY()), "the click landed on nothing");
    }

    @Test
    void the_panel_covers_85_percent_of_the_screen_centred() {
        Panel panel = panel();
        Rect at = panel.panel();

        assertEquals(144, at.x);
        assertEquals(81, at.y);
        assertEquals(WIDTH - 2 * 144, at.width);
        assertEquals(HEIGHT - 2 * 81, at.height);
    }

    @Test
    void every_switchable_feature_has_a_tile_with_its_name_and_its_state() {
        Panel panel = panel();
        FakeCanvas canvas = render(panel);

        for (SettingsScreen.Row row : new SettingsScreen(settings, feature -> true, () -> { }).rows()) {
            assertTrue(canvas.drew(row.name()), row.name() + " has no tile: " + canvas.texts());
            assertNotNull(panel.switchOf(row.feature()), row.name() + "'s button is not on view");
        }
        assertTrue(canvas.drew("ENABLED"), canvas.texts().toString());
    }

    @Test
    void settings_runs_down_the_strip_one_capital_at_a_time() {
        Panel panel = panel();
        FakeCanvas canvas = render(panel);

        Rect strip = panel.panel();
        int lastY = -1;
        for (char letter : "SETTINGS".toCharArray()) {
            FakeCanvas.Drawn drawn = null;
            for (FakeCanvas.Drawn each : canvas.drawn) {
                if (String.valueOf(letter).equals(each.text()) && each.y() > lastY) {
                    drawn = each;
                    break;
                }
            }
            assertNotNull(drawn, "no " + letter + " below the last letter");
            assertTrue(drawn.x() >= strip.x && drawn.x() + drawn.width() <= strip.x + Math.round(5.2f * WIDTH / 100) + 4,
                    letter + " is outside the strip");
            lastY = drawn.y();
        }
        assertTrue(lastY < panel.editHudButton().y, "the letters run into Edit HUD");
    }

    @Test
    void a_tile_s_button_switches_the_feature_now_and_in_the_file() throws Exception {
        Panel panel = panel();
        render(panel);

        click(panel, panel.switchOf(Feature.FPS_READOUT));

        assertFalse(settings.get(Settings.FPS_READOUT));
        assertTrue(Files.readString(configDir.resolve("ash.properties")).contains("\nfps-readout.enabled=false\n"));
        assertTrue(render(panel).drew("DISABLED"));
    }

    @Test
    void a_feature_that_did_not_load_reads_unavailable_and_cannot_be_switched() {
        Panel panel = panel(feature -> feature != Feature.TOGGLE_SPRINT, WIDTH, HEIGHT);
        FakeCanvas before = render(panel);
        assertTrue(before.drew("UNAVAILABLE"), before.texts().toString());

        click(panel, panel.switchOf(Feature.TOGGLE_SPRINT));

        assertTrue(settings.get(Settings.TOGGLE_SPRINT), "an unavailable feature was switched");
        assertTrue(render(panel).drew("Toggle sprint did not load. An update to ash will fix it."));
    }

    @Test
    void the_tile_or_its_gear_opens_a_feature_s_options_and_escape_goes_back_then_closes() {
        Panel panel = panel();
        render(panel);
        Rect gear = panel.optionsLinkOf(Feature.CROSSHAIR);
        assertNotNull(gear, "the crosshair's tile has no gear");
        assertNull(panel.optionsLinkOf(Feature.FPS_READOUT), "a feature with no options has a gear");

        click(panel, gear);
        render(panel);
        assertNull(panel.tileOf(Feature.CROSSHAIR), "the tiles are still showing under the options");
        assertNotNull(panel.backLink(), "the options page did not open");

        panel.keyPressed(Key.ESCAPE);
        render(panel);
        assertNotNull(panel.tileOf(Feature.CROSSHAIR), "Escape did not go back to the tiles");
        assertEquals(List.of(), closed);

        Rect tile = panel.tileOf(Feature.CROSSHAIR);
        panel.mouseClicked(tile.centreX(), tile.y + tile.height / 3);
        render(panel);
        assertNotNull(panel.backLink(), "clicking the tile's body did not open its options");

        panel.keyPressed(Key.ESCAPE);
        panel.keyPressed(Key.ESCAPE);
        assertEquals(List.of("closed"), closed, "Escape on the tiles did not close the panel");
    }

    @Test
    void edit_hud_says_it_is_coming_soon_for_a_moment() {
        Panel panel = panel();
        render(panel);

        click(panel, panel.editHudButton());
        assertTrue(render(panel).drew("Edit HUD is coming soon."));

        now.addAndGet(3_000_000_000L);
        assertFalse(render(panel).drew("Edit HUD is coming soon."), "the message never went away");
    }

    @Test
    void the_gear_opens_ash_s_own_settings_and_again_goes_back() {
        Panel panel = panel();
        render(panel);

        click(panel, panel.gearButton());
        assertTrue(render(panel).drew("ash settings"));
        assertNull(panel.tileOf(Feature.CROSSHAIR));

        click(panel, panel.gearButton());
        render(panel);
        assertNotNull(panel.tileOf(Feature.CROSSHAIR));
    }

    @Test
    void a_click_outside_the_panel_lands_on_nothing() {
        Panel panel = panel();
        render(panel);

        assertFalse(panel.mouseClicked(10, 10));
        assertTrue(settings.get(Settings.FPS_READOUT));
    }

    @Test
    void at_common_window_sizes_every_tile_and_its_text_stays_inside_the_panel() throws Exception {
        for (int[] size : new int[][] {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160}}) {
            Panel panel = panel(feature -> true, size[0], size[1]);
            FakeCanvas canvas = render(panel, size[0], size[1]);
            Rect inside = panel.panel();
            for (FakeCanvas.Drawn drawn : canvas.drawn) {
                if (drawn.text() == null) {
                    continue;
                }
                assertTrue(inside.contains(drawn.x(), drawn.y())
                        && inside.contains(drawn.x() + drawn.width() - 1, drawn.y() + drawn.height() - 1),
                        "\"" + drawn.text() + "\" leaves the panel at " + size[0] + "x" + size[1]);
            }
            if (size[0] == 1920) {
                // For a person to look at; nothing checks it.
                canvas.save(new File("build/ui/panel-" + size[0] + "x" + size[1] + ".png"));
            }
        }
    }
}
