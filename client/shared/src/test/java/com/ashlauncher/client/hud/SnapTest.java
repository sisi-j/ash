package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Where a dragged HUD item snaps, and the guides that show it (spec 0007, The HUD editor). */
class SnapTest {

    private static final int W = 427;
    private static final int H = 240;

    @Test
    void within_four_gui_pixels_of_a_screen_edge_it_lies_flush_against_it() {
        Snap snap = Snap.of(3, 50, 40, 9, W, H, List.of());
        assertEquals(0, snap.x());
        assertEquals(50, snap.y(), "nothing was near its top or bottom");
        assertEquals(List.of(Snap.Guide.vertical(0)), snap.guides());

        Snap right = Snap.of(W - 40 - 4, 50, 40, 9, W, H, List.of());
        assertEquals(W - 40, right.x());
        assertEquals(List.of(Snap.Guide.vertical(W)), right.guides());
    }

    @Test
    void five_pixels_away_is_too_far_to_snap() {
        Snap snap = Snap.of(5, 50, 40, 9, W, H, List.of());

        assertEquals(5, snap.x());
        assertTrue(snap.guides().isEmpty());
    }

    @Test
    void its_middle_snaps_to_the_screens_centre_lines() {
        // 427 wide: the centre line is 213; a 40-wide box centred there starts at 193.
        Snap snap = Snap.of(195, 118, 40, 9, W, H, List.of());

        assertEquals(193, snap.x());
        assertEquals(120 - 4, snap.y(), "its middle did not meet the horizontal centre line");
        assertEquals(List.of(Snap.Guide.vertical(213), Snap.Guide.horizontal(120)), snap.guides());
    }

    @Test
    void it_lines_up_with_the_edges_and_middles_of_other_items() {
        Snap.Box fps = new Snap.Box(4, 4, 42, 9);

        // Its left edge two from the frame rate's: a column lines up.
        assertEquals(4, Snap.of(6, 60, 30, 9, W, H, List.of(fps)).x());
        // Its top three below the frame rate's bottom: they stack.
        assertEquals(13, Snap.of(80, 16, 30, 9, W, H, List.of(fps)).y());
        // Its right edge to the frame rate's right edge.
        assertEquals(46 - 30, Snap.of(18, 60, 30, 9, W, H, List.of(fps)).x());
    }

    @Test
    void the_nearest_line_wins() {
        Snap.Box other = new Snap.Box(3, 100, 20, 9);

        // Its left edge is 2 from the screen's and 1 from the other item's.
        Snap snap = Snap.of(2, 50, 30, 9, W, H, List.of(other));

        assertEquals(3, snap.x());
        assertEquals(List.of(Snap.Guide.vertical(3)), snap.guides());
    }

    @Test
    void an_edge_never_snaps_to_a_centre_line_nor_a_middle_to_an_edge() {
        // Its top is 2 from the horizontal centre line and its middle 8:
        // only a middle meets a centre line, so it stays.
        assertEquals(118, Snap.of(80, 118, 30, 21, W, H, List.of()).y());
    }
}
