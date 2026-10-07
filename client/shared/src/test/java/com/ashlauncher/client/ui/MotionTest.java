package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The panel's motion, asked about moments: lengths, curves and where things are at each. */
class MotionTest {

    private static final long MS = Motion.MS;
    private static final float CLOSE = 0.002f;

    @Test
    void the_ease_out_is_the_mockups_curve() {
        // cubic-bezier(0.16, 1, 0.3, 1): a third of the way through, it is
        // most of the way there, and it ends exactly at its end.
        assertEquals(0, Motion.easeOut(0), CLOSE);
        assertTrue(Motion.easeOut(1 / 3f) > 0.85f, "not fast enough at the start: " + Motion.easeOut(1 / 3f));
        assertEquals(1, Motion.easeOut(1), CLOSE);
        float last = 0;
        for (int i = 1; i <= 100; i++) {
            float at = Motion.easeOut(i / 100f);
            assertTrue(at >= last - 1e-4f, "it went backwards at " + i + "%");
            last = at;
        }
    }

    @Test
    void the_css_curves_are_where_css_puts_them() {
        // Values from the curves themselves: CSS ease-in at a half is about
        // 0.315, and ease-out about 0.685, mirror images of each other.
        assertEquals(0.315f, Motion.easeIn(0.5f), 0.005f);
        assertEquals(0.685f, Motion.fadeOut(0.5f), 0.005f);
    }

    @Test
    void opening_takes_350_ms_and_ends_in_place_and_fully_visible() {
        assertEquals(0, Motion.openOpacity(0), CLOSE);
        assertEquals(Motion.OPEN_RISE, Motion.openRise(0), CLOSE);
        assertTrue(Motion.openOpacity(175 * MS) > 0.5f && Motion.openOpacity(175 * MS) < 1);
        assertEquals(1, Motion.openOpacity(350 * MS), CLOSE);
        assertEquals(0, Motion.openRise(350 * MS), CLOSE);
        assertEquals(0, Motion.openRise(10_000 * MS), CLOSE);
    }

    @Test
    void closing_is_the_reverse_in_half_the_time() {
        assertEquals(Motion.OPEN / 2, Motion.CLOSE);
        assertEquals(1, Motion.closeOpacity(0), CLOSE);
        assertEquals(0, Motion.closeSink(0), CLOSE);
        assertFalse(Motion.closed(174 * MS));
        assertTrue(Motion.closed(175 * MS));
        assertEquals(0, Motion.closeOpacity(175 * MS), CLOSE);
        assertEquals(Motion.CLOSE_SINK, Motion.closeSink(175 * MS), CLOSE);
    }

    @Test
    void the_tiles_arrive_one_just_after_another() {
        // The third tile starts 90 + 2 x 28 ms in, and is in place 300 ms later.
        assertEquals(146 * MS, Motion.tileDelay(2, true));
        assertEquals(0, Motion.tileOpacity(2, 146 * MS, true), CLOSE);
        assertEquals(Motion.TILE_RISE_UNITS, Motion.tileRise(2, 100 * MS, true), CLOSE);
        assertEquals(1, Motion.tileOpacity(2, 446 * MS, true), CLOSE);
        assertEquals(0, Motion.tileRise(2, 446 * MS, true), CLOSE);
        assertTrue(Motion.tileOpacity(0, 200 * MS, true) > Motion.tileOpacity(3, 200 * MS, true),
                "a later tile was ahead of an earlier one");
    }

    @Test
    void unstaggered_tiles_arrive_together() {
        assertEquals(Motion.tileOpacity(0, 120 * MS, false), Motion.tileOpacity(7, 120 * MS, false), CLOSE);
        assertTrue(Motion.tilesSettled(8, 300 * MS, false));
        assertFalse(Motion.tilesSettled(8, 300 * MS, true), "eight staggered tiles settle after the last one's delay");
        assertTrue(Motion.tilesSettled(8, (90 + 7 * 28 + 300) * MS, true));
    }

    @Test
    void a_page_leaves_in_110_ms_then_the_next_fades_in() {
        assertTrue(Motion.pageLeaving(109 * MS));
        assertFalse(Motion.pageLeaving(110 * MS));
        assertEquals(1, Motion.leavingOpacity(0), CLOSE);
        assertEquals(0, Motion.leavingOpacity(110 * MS), CLOSE);
        assertEquals(Motion.PAGE_LEAVE_DROP_UNITS, Motion.leavingDrop(110 * MS), CLOSE);

        assertEquals(0, Motion.enteringOpacity(110 * MS), CLOSE);
        assertEquals(1, Motion.enteringOpacity(290 * MS), CLOSE);
        assertEquals(Motion.PAGE_ENTER_RISE_UNITS, Motion.enteringRise(110 * MS), CLOSE);
        assertEquals(0, Motion.enteringRise(330 * MS), CLOSE);
        assertFalse(Motion.pageSettled(329 * MS));
        assertTrue(Motion.pageSettled(330 * MS));
    }

    @Test
    void a_switch_moves_fast_then_eases_out() {
        assertEquals(0, Motion.switchPosition(true, 0), CLOSE);
        assertTrue(Motion.switchPosition(true, 100 * MS) > 0.8f, "not fast at the start");
        assertEquals(1, Motion.switchPosition(true, 300 * MS), CLOSE);
        assertEquals(1, Motion.switchPosition(false, 0), CLOSE);
        assertEquals(0, Motion.switchPosition(false, 300 * MS), CLOSE);
    }

    @Test
    void a_shake_goes_left_right_left_right_and_settles_in_320_ms() {
        assertEquals(0, Motion.shake(0), CLOSE);
        assertEquals(-0.35f, Motion.shake(64 * MS), CLOSE);
        assertEquals(0.3f, Motion.shake(128 * MS), CLOSE);
        assertEquals(-0.2f, Motion.shake(192 * MS), CLOSE);
        assertEquals(0.1f, Motion.shake(256 * MS), CLOSE);
        assertEquals(0, Motion.shake(320 * MS), CLOSE);
        assertEquals(0, Motion.shake(1_000 * MS), CLOSE);
    }
}
