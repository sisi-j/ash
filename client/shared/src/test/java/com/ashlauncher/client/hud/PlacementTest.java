package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * A readout's place, as an anchor and an offset, across every change of
 * screen a player makes. The sizes are GUI sizes the two targets really give:
 * 1920 by 1080 at GUI scale 2 is 960 by 540, at 3 it is 640 by 360, at 4 it
 * is 480 by 270; 1280 by 720 at 3 is 427 by 240; 2560 by 1440 at 2 is 1280
 * by 720.
 */
class PlacementTest {

    /** "144 FPS" in the game's font, one line. */
    private static final int W = 41;
    private static final int H = 9;

    @Test
    void dropped_anywhere_it_is_exactly_where_it_was_dropped() {
        for (int x = 0; x <= 960 - W; x += 37) {
            for (int y = 0; y <= 540 - H; y += 23) {
                Placement placed = Placement.nearest(x, y, W, H, 960, 540);
                assertEquals(x, placed.x(W, 960), "across, dropped at " + x + "," + y + " as " + placed);
                assertEquals(y, placed.y(H, 540), "down, dropped at " + x + "," + y + " as " + placed);
            }
        }
    }

    @Test
    void it_is_anchored_to_the_third_of_the_screen_its_middle_is_in() {
        assertEquals(Anchor.TOP_LEFT, Placement.nearest(4, 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.TOP_RIGHT, Placement.nearest(960 - W - 4, 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.BOTTOM_LEFT, Placement.nearest(4, 540 - H - 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.BOTTOM_RIGHT, Placement.nearest(960 - W - 4, 540 - H - 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.CENTRE, Placement.nearest(460, 265, W, H, 960, 540).anchor());
        assertEquals(Anchor.TOP, Placement.nearest(460, 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.RIGHT, Placement.nearest(960 - W - 4, 265, W, H, 960, 540).anchor());
        assertEquals(Anchor.BOTTOM, Placement.nearest(460, 540 - H - 4, W, H, 960, 540).anchor());
        assertEquals(Anchor.LEFT, Placement.nearest(4, 265, W, H, 960, 540).anchor());
    }

    @Test
    void a_corner_readout_keeps_its_distance_from_its_corner_at_every_size_and_gui_scale() {
        Placement placed = Placement.nearest(960 - W - 6, 540 - H - 10, W, H, 960, 540);

        int[][] screens = {{960, 540}, {640, 360}, {480, 270}, {427, 240}, {1280, 720}};
        for (int[] screen : screens) {
            assertEquals(6, screen[0] - W - placed.x(W, screen[0]), "from the right on " + screen[0]);
            assertEquals(10, screen[1] - H - placed.y(H, screen[1]), "from the bottom on " + screen[1]);
        }
    }

    @Test
    void a_middle_readout_keeps_its_distance_from_the_middle() {
        // 20 right of centred and 30 above.
        Placement placed = Placement.nearest((960 - W) / 2 + 20, (540 - H) / 2 - 30, W, H, 960, 540);

        assertEquals(Anchor.CENTRE, placed.anchor());
        assertEquals((427 - W) / 2 + 20, placed.x(W, 427));
        assertEquals((240 - H) / 2 - 30, placed.y(H, 240));
    }

    @Test
    void a_number_that_grows_a_digit_grows_away_from_its_edge() {
        Placement right = new Placement(Anchor.TOP_RIGHT, 4, 4);

        // "99 FPS" then "100 FPS": the right edge stays 4 in.
        assertEquals(427 - 4, right.x(35, 427) + 35);
        assertEquals(427 - 4, right.x(41, 427) + 41);
    }

    @Test
    void it_cannot_be_dropped_off_screen() {
        Placement pastBottomRight = Placement.nearest(2000, 2000, W, H, 960, 540);
        assertEquals(960 - W, pastBottomRight.x(W, 960));
        assertEquals(540 - H, pastBottomRight.y(H, 540));

        Placement pastTopLeft = Placement.nearest(-50, -50, W, H, 960, 540);
        assertEquals(0, pastTopLeft.x(W, 960));
        assertEquals(0, pastTopLeft.y(H, 540));
    }

    @Test
    void a_smaller_screen_never_leaves_it_off_the_edge() {
        // Put 600 in from the left on a wide screen, then the window shrinks
        // to the smallest GUI either target draws.
        Placement wide = new Placement(Anchor.LEFT, 600, 0);

        assertEquals(320 - W, wide.x(W, 320), "brought back to the right edge");
        Placement farDown = new Placement(Anchor.TOP_LEFT, 4, 500);
        assertEquals(240 - H, farDown.y(H, 240), "brought back to the bottom edge");
        Placement farUp = new Placement(Anchor.BOTTOM, 0, 500);
        assertEquals(0, farUp.y(H, 240), "brought back to the top edge");
    }

    @Test
    void a_readout_wider_than_the_screen_keeps_its_start_on_it() {
        assertEquals(0, new Placement(Anchor.TOP_RIGHT, 4, 4).x(500, 427));
    }

    @Test
    void it_is_written_and_read_back_as_the_settings_file_has_it() {
        Placement placed = new Placement(Anchor.BOTTOM_RIGHT, 6, -3);

        assertEquals("bottom-right 6 -3", placed.format());
        assertEquals(placed, Placement.parse("bottom-right 6 -3"));
        assertEquals(placed, Placement.parse("  Bottom-Right   6\t-3 "), "as a hand edit might have it");
    }

    @Test
    void anything_else_is_not_a_placement() {
        assertNull(Placement.parse(""));
        assertNull(Placement.parse("top-left"));
        assertNull(Placement.parse("top-left 4"));
        assertNull(Placement.parse("top-left 4 4 4"));
        assertNull(Placement.parse("middle 4 4"));
        assertNull(Placement.parse("top-left four 4"));
        assertNull(Placement.parse("top-left 4 999999999"));
    }
}
