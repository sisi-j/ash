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

    /**
     * Any centre will do: the mixin passes the centre of the game's own
     * crosshair, which each target places differently, and the crosshair
     * draws on whatever it is given.
     */
    private static int centreX(FakeHudSurface surface) {
        return surface.width() / 2;
    }

    private static int centreY(FakeHudSurface surface) {
        return surface.height() / 2;
    }

    private static boolean draw(Crosshair crosshair, FakeHudSurface surface) {
        return crosshair.draw(surface, centreX(surface), centreY(surface));
    }

    @Test
    void switched_off_it_draws_nothing_and_leaves_the_game_s_crosshair_to_draw() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        boolean drew = draw(new Crosshair(() -> false, Cross.DEFAULT), surface);

        assertFalse(drew, "said it drew, so the game's crosshair would have been suppressed for nothing");
        assertEquals(0, surface.fills().size());
    }

    @Test
    void the_default_is_a_white_plus_with_a_dark_outline_centred_where_the_game_s_would_be() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        int cx = centreX(surface);
        int cy = centreY(surface);

        boolean drew = draw(new Crosshair(() -> true, Cross.DEFAULT), surface);

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
    void it_centres_on_the_pixel_it_is_given_even_off_the_middle_of_the_screen() {
        // 1.21.11 puts its crosshair one pixel up and left of the middle of
        // an even-sized screen, and 1.8.9 on it. The crosshair takes the
        // centre from the game's own crosshair call rather than working one
        // out, so it cannot disagree with either.
        for (int[] centre : new int[][] {{213, 120}, {212, 119}, {5, 5}}) {
            FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

            new Crosshair(() -> true, Cross.DEFAULT).draw(surface, centre[0], centre[1]);

            assertEquals(WHITE, surface.pixelAt(centre[0], centre[1]), "at " + centre[0] + "," + centre[1]);
            assertEquals(OUTLINE, surface.pixelAt(centre[0] + 1, centre[1] + 1), "at " + centre[0] + "," + centre[1]);
        }
    }

    @Test
    void a_gap_leaves_the_centre_open_and_the_arms_beyond_it() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        int cx = centreX(surface);
        int cy = centreY(surface);
        Cross gapped = new Cross(3, 2, 1, WHITE, false);

        draw(new Crosshair(() -> true, gapped), surface);

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
                    draw(new Crosshair(() -> true, new Cross(arm, gap, thickness, WHITE, false)), surface);

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

        draw(new Crosshair(() -> true, new Cross(4, 1, 3, WHITE, false)), surface);

        for (int offset = -1; offset <= 1; offset++) {
            assertEquals(WHITE, surface.pixelAt(cx + 3, cy + offset), "row " + offset + " of the right arm");
        }
        assertEquals(EMPTY, surface.pixelAt(cx + 3, cy + 2), "the right arm is thicker below than above");
        assertEquals(EMPTY, surface.pixelAt(cx + 3, cy - 2), "the right arm is thicker above than below");
    }

    @Test
    void without_an_outline_nothing_dark_is_drawn() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        draw(new Crosshair(() -> true, new Cross(4, 0, 1, WHITE, false)), surface);

        assertFalse(surface.fills().isEmpty(), "the test proves nothing if nothing was drawn");
        for (FakeHudSurface.Fill fill : surface.fills()) {
            assertEquals(WHITE, fill.colour(), "drew " + fill);
        }
    }

    @Test
    void switching_it_off_in_game_hands_the_next_frame_back_to_the_game() {
        AtomicBoolean on = new AtomicBoolean(true);
        Crosshair crosshair = new Crosshair(on::get, Cross.DEFAULT);
        assertTrue(draw(crosshair, FakeHudSurface.ofTypicalSize()), "the test proves nothing if it never drew");

        on.set(false);

        assertFalse(draw(crosshair, FakeHudSurface.ofTypicalSize()));
    }

    @Test
    void with_no_crosshair_installed_the_hook_leaves_the_game_s_crosshair_alone() {
        CrosshairHook.install(null);

        assertFalse(CrosshairHook.draw(FakeHudSurface.ofTypicalSize(), 213, 120));
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
