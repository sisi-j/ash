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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The panel's motion (#66) as a player sees it, on a clock the test sets:
 * what is where, and how visible, at chosen moments - and that a click
 * mid-motion lands on what is shown there.
 */
class PanelMotionTest {

    private static final long MS = Motion.MS;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @TempDir
    Path configDir;

    private final AtomicLong now = new AtomicLong(1_000 * MS);
    private final List<String> closed = new ArrayList<>();
    private Settings settings;

    private Panel panel(java.util.function.Predicate<Feature> landed) {
        settings = Settings.load(configDir);
        Panel panel = new Panel(new SettingsScreen(settings, landed, () -> { }), () -> closed.add("closed"), now::get);
        panel.resize(WIDTH, HEIGHT);
        return panel;
    }

    private Panel panel() {
        return panel(feature -> true);
    }

    private FakeCanvas at(Panel panel, long msSinceStart) {
        now.set(1_000 * MS + msSinceStart * MS);
        FakeCanvas canvas = new FakeCanvas(WIDTH, HEIGHT);
        panel.render(canvas, -1, -1);
        return canvas;
    }

    @Test
    void opening_the_panel_rises_and_fades_in_over_350_ms() {
        Panel panel = panel();
        at(panel, 0);

        FakeCanvas early = at(panel, 100);
        FakeCanvas done = at(panel, 700);

        FakeCanvas.Drawn s = early.textDrawn("S");
        FakeCanvas.Drawn settled = done.textDrawn("S");
        assertNotNull(s, "nothing drawn 100 ms in");
        assertTrue(s.y() > settled.y(), "not below its place while rising: " + s.y() + " vs " + settled.y());
        assertTrue(s.opacity() > 0f && s.opacity() < 1f, "not part-way faded in: " + s.opacity());
        assertEquals(1f, settled.opacity(), 0.001f);
        assertFalse(panel.animating(), "still moving after it opened");
    }

    @Test
    void closing_sinks_and_fades_away_then_closes_the_screen() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 500);

        panel.requestClose();
        FakeCanvas closing = at(panel, 560);
        assertTrue(closed.isEmpty(), "closed before it had faded");
        assertTrue(closing.textDrawn("S").opacity() < 1f);

        at(panel, 500 + 175);
        assertEquals(List.of("closed"), closed, "the screen did not close once the panel had gone");
        at(panel, 1_000);
        assertEquals(1, closed.size(), "closed twice");
    }

    @Test
    void escape_on_the_tiles_closes_through_the_same_motion() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 500);

        panel.keyPressed(Key.ESCAPE);
        assertTrue(closed.isEmpty());
        at(panel, 700);
        assertEquals(List.of("closed"), closed);
    }

    @Test
    void the_tiles_rise_in_one_just_after_another() {
        Panel panel = panel();
        at(panel, 0);

        FakeCanvas canvas = at(panel, 250);

        // 250 ms in: the first tile is well on its way; the eighth starts at 90 + 7 x 28 = 286 ms.
        FakeCanvas.Drawn first = canvas.textDrawn("FPS readout");
        assertNotNull(first);
        assertTrue(first.opacity() > 0.2f, "the first tile has barely started: " + first.opacity());
        assertNull(canvas.textDrawn("Hit colour"), "the last tile came in with the first");

        FakeCanvas later = at(panel, 1_000);
        assertEquals(1f, later.textDrawn("Hit colour").opacity(), 0.001f);
    }

    @Test
    void a_page_change_lets_the_tiles_leave_then_the_page_rise_in() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 1_000);
        Rect gear = panel.optionsLinkOf(Feature.CROSSHAIR);
        panel.mouseClicked(gear.centreX(), gear.centreY());

        FakeCanvas leaving = at(panel, 1_050);
        assertNotNull(leaving.textDrawn("FPS readout"), "the tiles did not stay to leave");
        assertTrue(leaving.textDrawn("FPS readout").opacity() < 1f);
        assertNull(leaving.textDrawn("Shape"), "the page arrived before the tiles had left");

        FakeCanvas entering = at(panel, 1_000 + 110 + 60);
        FakeCanvas.Drawn shape = entering.textDrawn("Shape");
        assertNotNull(shape, "the page did not come in once the tiles had left");
        assertTrue(shape.opacity() < 1f, "not fading in");

        FakeCanvas arrived = at(panel, 1_500);
        assertEquals(1f, arrived.textDrawn("Shape").opacity(), 0.001f);
        assertTrue(shape.y() > arrived.textDrawn("Shape").y(), "not rising into place");
    }

    @Test
    void a_tile_that_cannot_be_switched_shakes_when_pressed() {
        Panel panel = panel(feature -> feature != Feature.TOGGLE_SPRINT);
        at(panel, 0);
        int still = at(panel, 1_000).textDrawn("Toggle sprint").x();
        Rect toggle = panel.switchOf(Feature.TOGGLE_SPRINT);

        panel.mouseClicked(toggle.centreX(), toggle.centreY());

        // A fifth of the way through its shake, it is 0.35 units to the left.
        assertEquals(still - Math.round(0.35f * WIDTH / 100f), at(panel, 1_064).textDrawn("Toggle sprint").x(), 1);
        assertEquals(still, at(panel, 1_400).textDrawn("Toggle sprint").x(), "it did not settle back");
    }

    @Test
    void a_click_while_the_panel_rises_lands_on_what_is_shown_there() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 2_000);
        Rect toggle = panel.switchOf(Feature.FPS_READOUT);
        Panel fresh = panel();
        at(fresh, 0);
        FakeCanvas rising = at(fresh, 120);
        FakeCanvas settledFrame = at(panel, 2_000);
        int shownBelow = rising.textDrawn("S").y() - settledFrame.textDrawn("S").y();
        assertTrue(shownBelow > 0, "the test proves nothing if the panel is already in place");

        // Where the FPS readout's button is shown just now, below its place.
        int y = toggle.centreY() + shownBelow;
        int tileRise = Math.round(Motion.tileRise(0, 120 * MS, true) * WIDTH / 100f);
        fresh.mouseClicked(toggle.centreX(), y + tileRise);

        assertFalse(settings.get(Settings.FPS_READOUT), "the click missed the button it was shown on");
    }

    @Test
    void a_colour_code_that_is_not_a_colour_is_refused_with_a_shake_and_a_reason() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 1_000);
        Rect gear = panel.optionsLinkOf(Feature.CROSSHAIR);
        panel.mouseClicked(gear.centreX(), gear.centreY());
        at(panel, 1_500);
        Rect chip = panel.colourChipOf(Settings.CROSSHAIR_COLOUR);
        panel.mouseClicked(chip.centreX(), chip.centreY());
        at(panel, 1_520);
        Rect box = panel.hexBoxOf(Settings.CROSSHAIR_COLOUR);
        panel.mouseClicked(box.centreX(), box.centreY());
        for (int i = 0; i < 7; i++) {
            panel.keyPressed(Key.BACKSPACE);
        }
        "#12".codePoints().forEach(panel::charTyped);

        panel.keyPressed(Key.ENTER);

        assertEquals(0xFFFFFFFF, (int) settings.get(Settings.CROSSHAIR_COLOUR), "half a colour changed the colour");
        FakeCanvas shaking = at(panel, 1_564);
        assertTrue(shaking.drew("That isn't a colour. Use six hex digits, like #FA3A2F."), "drew " + shaking.texts());
        int shaken = shaking.textDrawn("#FFFFFF").x();
        int settled = at(panel, 2_000).textDrawn("#FFFFFF").x();
        assertTrue(shaken < settled, "the box did not shake: " + shaken + " vs " + settled);
    }

    @Test
    void escape_while_typing_a_colour_is_a_change_of_mind_not_a_mistake() {
        Panel panel = panel();
        at(panel, 0);
        at(panel, 1_000);
        Rect gear = panel.optionsLinkOf(Feature.CROSSHAIR);
        panel.mouseClicked(gear.centreX(), gear.centreY());
        at(panel, 1_500);
        Rect chip = panel.colourChipOf(Settings.CROSSHAIR_COLOUR);
        panel.mouseClicked(chip.centreX(), chip.centreY());
        at(panel, 1_520);
        Rect box = panel.hexBoxOf(Settings.CROSSHAIR_COLOUR);
        panel.mouseClicked(box.centreX(), box.centreY());
        for (int i = 0; i < 7; i++) {
            panel.keyPressed(Key.BACKSPACE);
        }
        "#12".codePoints().forEach(panel::charTyped);

        panel.keyPressed(Key.ESCAPE);

        assertFalse(at(panel, 1_550).drew("That isn't a colour. Use six hex digits, like #FA3A2F."));
    }

    @Test
    void with_animations_off_everything_is_simply_where_it_ends_up() {
        Panel panel = panel();
        panel.setAnimations(false);
        FakeCanvas first = at(panel, 0);

        assertEquals(1f, first.textDrawn("S").opacity(), 0.001f);
        assertEquals(1f, first.textDrawn("Hit colour").opacity(), 0.001f);
        panel.requestClose();
        assertEquals(List.of("closed"), closed, "closing waited for a motion that is off");
    }

    /**
     * The acceptance's frame cost: the panel's own drawing while it opens,
     * against drawing it open. Moving cached pieces costs no more than
     * drawing them in place, so the opening's frames stay within the open
     * panel's own - checked as no more than half as much again, to leave
     * room for a test machine's noise. The numbers are printed for the PR.
     */
    @Test
    void the_opening_costs_no_more_to_draw_than_the_open_panel() {
        // Warm the shared raster cache first, as any frame after the first has it.
        Panel warm = panel();
        at(warm, 0);
        at(warm, 2_000);

        long[] opening = new long[35];
        long[] open = new long[35];
        Panel panel = panel();
        at(panel, 0);
        for (int i = 0; i < opening.length; i++) {
            opening[i] = timed(panel, i * 10L);
        }
        for (int i = 0; i < open.length; i++) {
            open[i] = timed(panel, 2_000 + i * 10L);
        }
        long openingMedian = median(opening);
        long openMedian = median(open);
        System.out.printf("panel frame cost: opening median %.2f ms, open median %.2f ms%n", openingMedian / 1e6,
                openMedian / 1e6);
        assertTrue(openingMedian <= openMedian * 3 / 2 + 2 * MS,
                "opening " + openingMedian / 1e6 + " ms against open " + openMedian / 1e6 + " ms");
    }

    private long timed(Panel panel, long ms) {
        now.set(1_000 * MS + ms * MS);
        FakeCanvas canvas = new FakeCanvas(WIDTH, HEIGHT);
        long start = System.nanoTime();
        panel.render(canvas, -1, -1);
        return System.nanoTime() - start;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
