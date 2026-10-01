package com.ashlauncher.client.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.hud.FakeHudSurface;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * ash's crosshair, judged by the pixels it leaves: the fake surface paints
 * each fill in order, so these ask what a player would see.
 */
class CrosshairTest {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int OUTLINE = Cross.OUTLINE_COLOUR;
    private static final int EMPTY = FakeHudSurface.EMPTY;

    /** Where the game centres its own crosshair: half the GUI size, rounded down. */
    private static int centreX(FakeHudSurface surface) {
        return surface.width() / 2;
    }

    private static int centreY(FakeHudSurface surface) {
        return surface.height() / 2;
    }

    @Test
    void switched_off_it_draws_nothing_and_leaves_the_game_s_crosshair_to_draw() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        boolean drew = new Crosshair(() -> false, Cross.DEFAULT).draw(surface);

        assertFalse(drew, "said it drew, so the game's crosshair would have been suppressed for nothing");
        assertEquals(0, surface.fills().size());
    }

    @Test
    void the_default_is_a_white_plus_with_a_dark_outline_centred_where_the_game_s_would_be() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        int cx = centreX(surface);
        int cy = centreY(surface);

        boolean drew = new Crosshair(() -> true, Cross.DEFAULT).draw(surface);

        assertTrue(drew);
        int arm = Cross.DEFAULT.arm();
        assertEquals(WHITE, surface.pixelAt(cx, cy), "the centre");
        for (int[] tip : new int[][] {{cx + arm, cy}, {cx - arm, cy}, {cx, cy + arm}, {cx, cy - arm}}) {
            assertEquals(WHITE, surface.pixelAt(tip[0], tip[1]), "an arm's tip at " + tip[0] + "," + tip[1]);
        }
        for (int[] edge : new int[][] {{cx + arm + 1, cy}, {cx - arm - 1, cy}, {cx, cy + arm + 1}, {cx, cy - arm - 1},
                {cx + 1, cy + 1}}) {
            assertEquals(OUTLINE, surface.pixelAt(edge[0], edge[1]), "the outline at " + edge[0] + "," + edge[1]);
        }
        assertEquals(EMPTY, surface.pixelAt(cx + arm + 2, cy), "drawn past its outline");
        assertEquals(EMPTY, surface.pixelAt(cx + 2, cy + 2), "a plus filled in its corners");
    }

    @Test
    void it_follows_the_screen_s_centre_on_any_size() {
        for (int[] size : new int[][] {{427, 240}, {320, 180}, {641, 361}}) {
            FakeHudSurface surface = new FakeHudSurface(size[0], size[1], 9);

            new Crosshair(() -> true, Cross.DEFAULT).draw(surface);

            assertEquals(WHITE, surface.pixelAt(size[0] / 2, size[1] / 2), "on " + size[0] + "x" + size[1]);
        }
    }

    @Test
    void a_gap_leaves_the_centre_open_and_the_arms_beyond_it() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        int cx = centreX(surface);
        int cy = centreY(surface);
        Cross gapped = new Cross(3, 2, 1, WHITE, false);

        new Crosshair(() -> true, gapped).draw(surface);

        assertEquals(EMPTY, surface.pixelAt(cx, cy), "the centre of a gapped cross was filled");
        assertEquals(EMPTY, surface.pixelAt(cx + 2, cy), "the gap was filled");
        assertEquals(WHITE, surface.pixelAt(cx + 3, cy), "the arm does not start straight after the gap");
        assertEquals(WHITE, surface.pixelAt(cx + 5, cy), "the arm is shorter than asked");
        assertEquals(EMPTY, surface.pixelAt(cx + 6, cy), "the arm is longer than asked");
        assertEquals(WHITE, surface.pixelAt(cx - 5, cy), "the arms are not mirrored");
    }

    @Test
    void every_size_spans_exactly_its_arms_gaps_and_thickness_on_both_axes() {
        // Odd and even thicknesses, with and without a gap: the drawn extent
        // is the same sum on both axes, so the cross is never lopsided.
        for (int thickness = 1; thickness <= 4; thickness++) {
            for (int gap = 0; gap <= 2; gap++) {
                for (int arm = 1; arm <= 6; arm++) {
                    FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
                    new Crosshair(() -> true, new Cross(arm, gap, thickness, WHITE, false)).draw(surface);

                    int expected = 2 * (arm + gap) + thickness;
                    String size = "arm " + arm + ", gap " + gap + ", thickness " + thickness;
                    assertEquals(expected, extent(surface, true), "width for " + size);
                    assertEquals(expected, extent(surface, false), "height for " + size);
                }
            }
        }
    }

    @Test
    void an_odd_thickness_puts_the_middle_of_each_bar_on_the_centre_pixel() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        int cx = centreX(surface);
        int cy = centreY(surface);

        new Crosshair(() -> true, new Cross(4, 1, 3, WHITE, false)).draw(surface);

        for (int offset = -1; offset <= 1; offset++) {
            assertEquals(WHITE, surface.pixelAt(cx + 3, cy + offset), "row " + offset + " of the right arm");
        }
        assertEquals(EMPTY, surface.pixelAt(cx + 3, cy + 2), "the right arm is thicker below than above");
        assertEquals(EMPTY, surface.pixelAt(cx + 3, cy - 2), "the right arm is thicker above than below");
    }

    @Test
    void without_an_outline_nothing_dark_is_drawn() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new Crosshair(() -> true, new Cross(4, 0, 1, WHITE, false)).draw(surface);

        assertFalse(surface.fills().isEmpty(), "the test proves nothing if nothing was drawn");
        for (FakeHudSurface.Fill fill : surface.fills()) {
            assertEquals(WHITE, fill.colour(), "drew " + fill);
        }
    }

    @Test
    void switching_it_off_in_game_hands_the_next_frame_back_to_the_game() {
        AtomicBoolean on = new AtomicBoolean(true);
        Crosshair crosshair = new Crosshair(on::get, Cross.DEFAULT);
        assertTrue(crosshair.draw(FakeHudSurface.ofTypicalSize()), "the test proves nothing if it never drew");

        on.set(false);

        assertFalse(crosshair.draw(FakeHudSurface.ofTypicalSize()));
    }

    @Test
    void with_no_crosshair_installed_the_hook_leaves_the_game_s_crosshair_alone() {
        CrosshairHook.install(null);

        assertFalse(CrosshairHook.draw(FakeHudSurface.ofTypicalSize()));
    }

    /**
     * From the first column (or row) with anything drawn in it to the last -
     * the bounding box, since a gap leaves empty columns inside the cross.
     */
    private static int extent(FakeHudSurface surface, boolean horizontal) {
        int limit = horizontal ? surface.width() : surface.height();
        int first = -1;
        int last = -1;
        for (int i = 0; i < limit; i++) {
            boolean any = false;
            for (int j = 0; j < (horizontal ? surface.height() : surface.width()) && !any; j++) {
                any = (horizontal ? surface.pixelAt(i, j) : surface.pixelAt(j, i)) != EMPTY;
            }
            if (any) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return first < 0 ? 0 : last - first + 1;
    }
}
