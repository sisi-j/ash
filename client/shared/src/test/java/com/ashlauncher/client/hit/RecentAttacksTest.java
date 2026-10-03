package com.ashlauncher.client.hit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * 1.8.9's match between "this entity was hurt", which names no attacker, and
 * the player's own recent attacks.
 */
class RecentAttacksTest {

    private final AtomicLong now = new AtomicLong(50_000);
    private final RecentAttacks attacks = new RecentAttacks(now::get);

    @Test
    void a_hurt_with_no_attack_is_not_the_player_s() {
        assertFalse(attacks.hurt(7));
    }

    @Test
    void a_hurt_of_the_entity_the_player_just_attacked_is_theirs() {
        attacks.attacked(7);
        now.addAndGet(120);

        assertTrue(attacks.hurt(7));
    }

    @Test
    void a_hurt_of_some_other_entity_is_not() {
        attacks.attacked(7);

        assertFalse(attacks.hurt(8));
    }

    @Test
    void the_window_includes_its_last_millisecond_and_nothing_after() {
        attacks.attacked(7);
        now.addAndGet(RecentAttacks.WINDOW_MILLIS);
        assertTrue(attacks.hurt(7), "at the end of the window");

        attacks.attacked(9);
        now.addAndGet(RecentAttacks.WINDOW_MILLIS + 1);
        assertFalse(attacks.hurt(9), "past the end of the window");
    }

    @Test
    void one_attack_lights_at_most_one_mark() {
        // The entity's next hurt inside the window is someone else's, or the
        // fire's: one swing confirmed once.
        attacks.attacked(7);

        assertTrue(attacks.hurt(7));
        assertFalse(attacks.hurt(7), "the same attack matched twice");
    }

    @Test
    void attacks_on_two_entities_in_quick_succession_both_match() {
        attacks.attacked(7);
        now.addAndGet(40);
        attacks.attacked(8);
        now.addAndGet(80);

        assertTrue(attacks.hurt(7));
        assertTrue(attacks.hurt(8));
    }

    @Test
    void attacking_the_same_entity_again_restarts_its_window() {
        attacks.attacked(7);
        now.addAndGet(RecentAttacks.WINDOW_MILLIS - 10);
        attacks.attacked(7);
        now.addAndGet(500);

        assertTrue(attacks.hurt(7));
    }

    @Test
    void however_many_entities_are_attacked_it_keeps_only_those_still_in_the_window() {
        for (int id = 0; id < 10_000; id++) {
            attacks.attacked(id);
            now.addAndGet(5);
        }

        assertTrue(attacks.size() <= RecentAttacks.WINDOW_MILLIS / 5 + 1, "kept " + attacks.size());
    }
}
