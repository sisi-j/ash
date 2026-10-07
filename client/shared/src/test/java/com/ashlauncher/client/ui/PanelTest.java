package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Anchor;
import com.ashlauncher.client.hud.FakeHudSurface;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.Placement;
import com.ashlauncher.client.ping.PingReadout;
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
    private final List<String> reported = new ArrayList<>();
    private final AtomicLong now = new AtomicLong();
    private Settings settings;
    /** The panel's model, for the readouts that draw from the same layout. */
    private SettingsScreen model;

    private Panel panel(Predicate<Feature> landed, int width, int height) {
        settings = Settings.load(configDir);
        model = new SettingsScreen(settings, landed, () -> reported.add("report"));
        Panel panel = new Panel(model, () -> closed.add("closed"), now::get);
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
    void a_tile_s_button_switches_the_feature_now_in_the_file_and_in_the_load_report() throws Exception {
        Panel panel = panel();
        render(panel);

        click(panel, panel.switchOf(Feature.FPS_READOUT));

        assertFalse(settings.get(Settings.FPS_READOUT));
        assertEquals(List.of("report"), reported, "the load report was not rewritten");
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
        assertNotNull(panel.optionsLinkOf(Feature.FPS_READOUT), "every tile has a gear, as the mockup has it");

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

    /**
     * Both readouts drawn once on a HUD the size this screen is at GUI scale
     * 2, as the game draws them beneath the screen, so the layout knows how
     * big each is.
     */
    private void drawReadouts() {
        HudLayout layout = model.hudLayout();
        FakeHudSurface hud = new FakeHudSurface(WIDTH / 2, HEIGHT / 2, 9);
        new FpsReadout(() -> 144, layout).draw(hud);
        new PingReadout(() -> 42, layout).draw(hud);
    }

    /** Where the FPS readout draws now, on the same HUD. */
    private FakeHudSurface.Text fpsDrawn() {
        FakeHudSurface hud = new FakeHudSurface(WIDTH / 2, HEIGHT / 2, 9);
        new FpsReadout(() -> 144, model.hudLayout()).draw(hud);
        return hud.onlyText();
    }

    private Panel editingHud() {
        Panel panel = panel();
        panel.setGuiScale(2);
        drawReadouts();
        render(panel);
        click(panel, panel.editHudButton());
        assertTrue(panel.editingHud());
        return panel;
    }

    @Test
    void edit_hud_puts_the_panel_away_and_outlines_each_readout_with_the_anchor_it_keeps() throws Exception {
        Panel panel = editingHud();

        FakeCanvas canvas = render(panel);
        canvas.save(new File("build/ui/edit-hud.png"));

        assertTrue(canvas.drew("FPS readout · Top left"), "drew " + canvas.texts());
        assertTrue(canvas.drew("Ping readout · Top left"), "drew " + canvas.texts());
        assertTrue(canvas.drew("Done") && canvas.drew("Reset"), "drew " + canvas.texts());
        assertFalse(canvas.drew("Crosshair"), "the panel's tiles are still up");
        // "144 FPS" at 4,4 in GUI units, six a character, a unit of room round
        // it, in real pixels at GUI scale 2.
        Rect box = panel.readoutBox(HudLayout.Readout.FPS);
        assertEquals((4 - 1) * 2, box.x);
        assertEquals((4 - 1) * 2, box.y);
        assertEquals((42 + 1) * 2, box.width);
        assertEquals((9 + 1) * 2, box.height);
        assertTrue(model.hudLayout().editing(), "the ping would not show in singleplayer to be moved");
    }

    @Test
    void a_dragged_readout_moves_with_the_mouse_and_is_saved_where_it_is_dropped() throws Exception {
        Panel panel = editingHud();
        Rect box = panel.readoutBox(HudLayout.Readout.FPS);

        assertTrue(panel.mouseClicked(box.centreX(), box.centreY()));
        panel.mouseDragged(WIDTH - 50, HEIGHT - 30);
        FakeCanvas dragging = render(panel);
        dragging.save(new File("build/ui/edit-hud-dragging.png"));
        assertTrue(dragging.drew("FPS readout · Bottom right"), "the tag does not follow the drag");
        assertEquals(new Placement(Anchor.TOP_LEFT, 4, 4), settings.get(Settings.FPS_READOUT_POSITION),
                "saved on every move of the mouse, not once on the drop");
        panel.mouseReleased();

        // Taken by its middle, which stays under the mouse: 49,16 in real
        // pixels is 20.5,4 into it in GUI units, and 1870,1050 is 935,525.
        assertEquals(915, fpsDrawn().x());
        assertEquals(521, fpsDrawn().y());
        assertTrue(Files.readAllLines(configDir.resolve("ash.properties")).contains(
                "fps-readout.position=bottom-right 3 10"));
        assertTrue(reported.contains("report"), "the load report was not rewritten");
    }

    @Test
    void a_readout_cannot_be_dragged_off_screen() {
        Panel panel = editingHud();
        Rect box = panel.readoutBox(HudLayout.Readout.FPS);

        panel.mouseClicked(box.centreX(), box.centreY());
        panel.mouseDragged(WIDTH * 3, HEIGHT * 3);
        panel.mouseReleased();

        assertEquals(WIDTH / 2 - 42, fpsDrawn().x());
        assertEquals(HEIGHT / 2 - 9, fpsDrawn().y());

        Rect moved = panel.readoutBox(HudLayout.Readout.FPS);
        panel.mouseClicked(moved.centreX(), moved.centreY());
        panel.mouseDragged(-500, -500);
        panel.mouseReleased();

        assertEquals(0, fpsDrawn().x());
        assertEquals(0, fpsDrawn().y());
    }

    @Test
    void reset_puts_both_readouts_back() {
        Panel panel = editingHud();
        settings.set(Settings.FPS_READOUT_POSITION, new Placement(Anchor.BOTTOM_RIGHT, 4, 4));
        settings.set(Settings.PING_READOUT_POSITION, new Placement(Anchor.TOP_RIGHT, 4, 4));

        click(panel, panel.resetReadoutsButton());

        assertEquals(Settings.FPS_READOUT_POSITION.fallback(), settings.get(Settings.FPS_READOUT_POSITION));
        assertEquals(Settings.PING_READOUT_POSITION.fallback(), settings.get(Settings.PING_READOUT_POSITION));
        assertTrue(panel.editingHud(), "Reset is not Done");
    }

    @Test
    void done_or_escape_goes_back_to_the_panel() {
        Panel panel = editingHud();
        click(panel, panel.doneButton());
        assertFalse(panel.editingHud());
        assertFalse(model.hudLayout().editing());
        assertTrue(render(panel).drew("Crosshair"), "the tiles did not come back");

        click(panel, panel.editHudButton());
        panel.keyPressed(Key.ESCAPE);
        assertFalse(panel.editingHud());
        assertTrue(closed.isEmpty(), "Escape in Edit HUD closed the whole screen");
    }

    @Test
    void closing_the_screen_mid_drag_leaves_the_readout_where_it_was() {
        Panel panel = editingHud();
        Rect box = panel.readoutBox(HudLayout.Readout.FPS);
        panel.mouseClicked(box.centreX(), box.centreY());
        panel.mouseDragged(WIDTH / 2, HEIGHT / 2);

        panel.closed();

        assertFalse(model.hudLayout().editing());
        assertEquals(4, fpsDrawn().x());
        assertEquals(4, fpsDrawn().y());
    }

    @Test
    void with_both_readouts_off_edit_hud_says_how_to_have_one_to_move() {
        Panel panel = editingHud();
        settings.set(Settings.FPS_READOUT, false);
        settings.set(Settings.PING_READOUT, false);

        FakeCanvas canvas = render(panel);

        assertNull(panel.readoutBox(HudLayout.Readout.FPS));
        assertTrue(canvas.drew("Switch the FPS or ping readout on to move it here."), "drew " + canvas.texts());
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

    // ---- icons, search and tabs (#65) ----

    private static void type(Panel panel, String text) {
        text.codePoints().forEach(panel::charTyped);
    }

    @Test
    void every_tile_shows_its_features_icon_under_its_name() throws Exception {
        Panel panel = panel();
        FakeCanvas canvas = render(panel);
        canvas.save(new File("build/ui/tiles-with-icons.png"));

        for (SettingsScreen.Row row : model.rows()) {
            assertNotNull(Panel.iconOf(row.feature()), row.name() + " has no icon");
            Rect glyph = panel.glyphIn(panel.tileOf(row.feature()));
            assertTrue(canvas.drawn.stream().anyMatch(d -> d.x() == glyph.x && d.y() == glyph.y
                    && d.width() == glyph.width && d.height() == glyph.height),
                    row.name() + "'s icon is not where its tile puts it");
        }
    }

    @Test
    void typing_on_the_tiles_searches_them_as_you_type() throws Exception {
        Panel panel = panel();
        render(panel);

        type(panel, "cr");
        FakeCanvas canvas = render(panel);
        canvas.save(new File("build/ui/tiles-searching.png"));

        assertEquals("cr", panel.query());
        assertNotNull(panel.tileOf(Feature.CROSSHAIR));
        assertNull(panel.tileOf(Feature.FPS_READOUT), "a tile whose name does not match is still up");
        assertTrue(canvas.drew("cr"), "the search box does not show what was typed: " + canvas.texts());

        panel.keyPressed(Key.BACKSPACE);
        assertEquals("c", panel.query());
        assertNotNull(panel.tileOf(Feature.HIT_COLOUR), "\"c\" is in Hit colour");
    }

    @Test
    void escape_clears_a_search_before_it_closes_anything() {
        Panel panel = panel();
        type(panel, "ping");

        panel.keyPressed(Key.ESCAPE);
        assertEquals("", panel.query());
        assertTrue(closed.isEmpty(), "Escape closed the panel on a player who was searching");

        panel.keyPressed(Key.ESCAPE);
        assertEquals(List.of("closed"), closed);
    }

    @Test
    void when_nothing_matches_it_says_so() {
        Panel panel = panel();
        type(panel, "zzz");

        FakeCanvas canvas = render(panel);

        assertTrue(canvas.drew("No feature matches “zzz”."), "drew " + canvas.texts());
        assertNull(panel.tileOf(Feature.CROSSHAIR));
    }

    @Test
    void the_search_box_shows_its_placeholder_until_something_is_typed() {
        Panel panel = panel();

        assertTrue(render(panel).drew("Search features"));
    }

    @Test
    void the_tabs_are_all_then_each_category_in_use_and_filter_the_tiles() throws Exception {
        Panel panel = panel();
        FakeCanvas canvas = render(panel);
        for (String tab : new String[] {"All", "PvP", "HUD", "Movement"}) {
            assertTrue(canvas.drew(tab), "no " + tab + " tab: " + canvas.texts());
        }

        click(panel, panel.tabOf(com.ashlauncher.client.settings.Category.HUD));
        render(panel).save(new File("build/ui/tiles-hud-tab.png"));

        assertNotNull(panel.tileOf(Feature.FPS_READOUT));
        assertNotNull(panel.tileOf(Feature.PING_READOUT));
        assertNull(panel.tileOf(Feature.CROSSHAIR), "a PvP tile is up on the HUD tab");

        click(panel, panel.tabOf(null));
        assertNotNull(panel.tileOf(Feature.CROSSHAIR), "All did not bring every tile back");
    }

    @Test
    void a_search_and_a_tab_together_both_have_to_match() {
        Panel panel = panel();
        click(panel, panel.tabOf(com.ashlauncher.client.settings.Category.PVP));
        type(panel, "hit");

        assertEquals(2, panel.visibleRows().size(), "Hit indicator and Hit colour, both PvP");
        assertNull(panel.tileOf(Feature.CROSSHAIR));
    }

    @Test
    void the_tiles_scroll_by_mouse_wheel_when_they_overflow_and_a_search_starts_at_the_top() {
        // Short enough that two rows of tiles do not fit.
        Panel panel = panel(feature -> true, 1920, 640);
        render(panel, 1920, 640);
        assertTrue(panel.maxScroll() > 0, "the test proves nothing if every tile fits");
        assertNotNull(panel.tileOf(Feature.FPS_READOUT));

        panel.mouseScrolled(-1);
        assertNull(panel.tileOf(Feature.FPS_READOUT), "the wheel did not scroll the first row away");
        panel.mouseScrolled(1);
        assertNotNull(panel.tileOf(Feature.FPS_READOUT), "the wheel did not scroll back");

        panel.mouseScrolled(-1);
        type(panel, "s");
        assertNotNull(panel.tileOf(panel.visibleRows().get(0).feature()), "a new search did not start at the top");
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
            int texts = 0;
            for (FakeCanvas.Drawn drawn : canvas.drawn) {
                if (drawn.text() == null) {
                    continue;
                }
                texts++;
                assertTrue(inside.contains(drawn.x(), drawn.y())
                        && inside.contains(drawn.x() + drawn.width() - 1, drawn.y() + drawn.height() - 1),
                        "\"" + drawn.text() + "\" leaves the panel at " + size[0] + "x" + size[1]);
            }
            // Every letter of SETTINGS, every tile's name and its button: a
            // check over no text at all would pass and prove nothing.
            int expected = "SETTINGS".length() + 2 * new SettingsScreen(settings, feature -> true, () -> { }).rows().size();
            assertTrue(texts >= expected, "only " + texts + " lines drawn at " + size[0] + "x" + size[1]);
            if (size[0] == 1920) {
                // For a person to look at; nothing checks it.
                canvas.save(new File("build/ui/panel-" + size[0] + "x" + size[1] + ".png"));
            }
        }
    }

    @Test
    void where_the_game_cannot_blur_the_panel_darkens_what_is_behind_it_more() {
        Panel panel = panel();
        int blurredCorner = render(panel).pixel(5, 5);

        panel.setBlurred(false);
        int unblurredCorner = render(panel).pixel(5, 5);

        assertTrue((unblurredCorner & 0xFF) < (blurredCorner & 0xFF),
                "no darker: " + Integer.toHexString(blurredCorner) + " then " + Integer.toHexString(unblurredCorner));
    }
}
