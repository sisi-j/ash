package com.ashlauncher.client.hit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.hud.FakeHudSurface;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The hit indicator's mark, judged by the pixels it leaves on a fake HUD and
 * driven by a clock the test moves by hand.
 */
class HitIndicatorTest {

    private static final int RED = 0xFFFF4D4D;
    private static final int CX = 213;
    private static final int CY = 119;

    private final AtomicLong now = new AtomicLong(10_000);
    private final AtomicBoolean on = new AtomicBoolean(true);
    private final AtomicInteger colour = new AtomicInteger(RED);
    private final AtomicInteger duration = new AtomicInteger(400);
    private final HitIndicator indicator =
            new HitIndicator(on::get, colour::get, duration::get, now::get);

    private FakeHudSurface draw() {
        return draw(FakeHudSurface.ofTypicalSize());
    }

    private FakeHudSurface draw(FakeHudSurface surface) {
        indicator.draw(surface, CX, CY);
        return surface;
    }

    @Test
    void with_no_confirmation_there_is_no_mark() {
        assertTrue(draw().fills().isEmpty());
    }

    @Test
    void a_confirmed_hit_marks_the_four_diagonals_around_the_crosshair_and_leaves_its_arms_clear() {
        indicator.confirmed();
        FakeHudSurface surface = draw();

        for (int[] corner : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            for (int d = HitIndicator.FROM; d <= HitIndicator.TO; d++) {
                int x = CX + corner[0] * d;
                int y = CY + corner[1] * d;
                assertEquals(RED, surface.pixelAt(x, y), "the mark at " + (x - CX) + "," + (y - CY));
            }
        }
        for (int d = 0; d <= HitIndicator.TO + 1; d++) {
            for (int[] arm : new int[][] {{d, 0}, {-d, 0}, {0, d}, {0, -d}}) {
                assertEquals(FakeHudSurface.EMPTY, surface.pixelAt(CX + arm[0], CY + arm[1]),
                        "the mark covers the crosshair's arm at " + arm[0] + "," + arm[1]);
            }
        }
    }

    @Test
    void no_pixel_of_the_mark_is_filled_twice() {
        indicator.confirmed();
        FakeHudSurface surface = draw();

        Set<Long> seen = new HashSet<>();
        for (FakeHudSurface.Fill fill : surface.fills()) {
            for (int x = fill.x(); x < fill.x() + fill.width(); x++) {
                for (int y = fill.y(); y < fill.y() + fill.height(); y++) {
                    assertTrue(seen.add(((long) x << 32) | (y & 0xFFFFFFFFL)), "filled twice at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void it_never_writes_anything_no_damage_and_no_health() {
        indicator.confirmed();

        assertTrue(draw().drawn().isEmpty(), "the mark wrote text");
    }

    @Test
    void it_shows_for_as_long_as_the_player_set_and_then_is_gone() {
        indicator.confirmed();

        now.addAndGet(350);
        assertFalse(draw().fills().isEmpty(), "gone before its time");
        now.addAndGet(50);
        assertTrue(draw().fills().isEmpty(), "still there after its time");
    }

    @Test
    void it_is_solid_for_the_first_half_then_fades_out() {
        indicator.confirmed();

        now.addAndGet(200);
        assertEquals(0xFF, alphaOfMark(), "faded in its first half");
        now.addAndGet(100);
        int halfway = alphaOfMark();
        assertTrue(halfway > 0x70 && halfway < 0x90, "three quarters through: " + Integer.toHexString(halfway));
    }

    @Test
    void the_fade_scales_the_player_s_own_opacity() {
        colour.set(0x80FF4D4D);
        indicator.confirmed();

        assertEquals(0x80, alphaOfMark());
        now.addAndGet(300);
        assertEquals(0x40, alphaOfMark(), 1);
    }

    @Test
    void too_faint_to_draw_alike_on_both_targets_it_draws_nothing() {
        indicator.confirmed();

        now.addAndGet(395);

        assertTrue(draw().fills().isEmpty(), "drew below the faintest alpha both targets draw the same");
    }

    @Test
    void a_new_hit_starts_it_again() {
        indicator.confirmed();
        now.addAndGet(350);
        indicator.confirmed();
        now.addAndGet(200);

        assertEquals(0xFF, alphaOfMark());
    }

    @Test
    void the_colour_and_the_duration_are_read_as_it_draws() {
        indicator.confirmed();
        colour.set(0xFF4DC3FF);
        assertEquals(0xFF4DC3FF, draw().fills().get(0).colour());

        duration.set(100);
        now.addAndGet(150);
        assertTrue(draw().fills().isEmpty(), "a shorter duration did not take effect");
    }

    @Test
    void switched_off_it_draws_nothing_and_a_hit_while_off_is_not_shown_once_switched_on() {
        on.set(false);
        indicator.confirmed();
        assertTrue(draw().fills().isEmpty());

        on.set(true);
        assertTrue(draw().fills().isEmpty(), "a hit from while it was off showed");
    }

    @Test
    void with_the_hud_hidden_it_draws_nothing() {
        indicator.confirmed();

        assertTrue(draw(FakeHudSurface.ofTypicalSize().withHudHidden()).fills().isEmpty());
    }

    private int alphaOfMark() {
        FakeHudSurface surface = draw();
        assertFalse(surface.fills().isEmpty(), "no mark");
        return surface.fills().get(0).colour() >>> 24;
    }
}
