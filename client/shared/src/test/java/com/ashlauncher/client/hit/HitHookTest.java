package com.ashlauncher.client.hit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.hud.FakeHudSurface;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What each target's mixins report, and whether it lights the mark: on
 * 1.21.11 the server names the attacker; on 1.8.9 the hurt is matched to the
 * player's own attack.
 */
class HitHookTest {

    private static final int PLAYER = 42;
    private static final int PIG = 7;
    private static final int SOMEONE_ELSE = 99;

    private final AtomicLong now = new AtomicLong(1_000);
    private final HitIndicator indicator = new HitIndicator(() -> true, () -> 0xFFFF4D4D, () -> 400, now::get);

    @AfterEach
    void uninstall() {
        HitHook.install(null, null);
    }

    private boolean marked() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        HitHook.draw(surface, 213, 119);
        return !surface.fills().isEmpty();
    }

    @Test
    void with_nothing_installed_every_report_is_ignored_and_nothing_draws() {
        HitHook.damageEvent(PIG, PLAYER, PLAYER);
        HitHook.attacked(PIG);
        HitHook.hurt(PIG);

        assertFalse(marked());
    }

    @Test
    void on_1_21_11_a_damage_event_naming_the_player_as_its_cause_marks() {
        HitHook.install(indicator, null);

        HitHook.damageEvent(PIG, PLAYER, PLAYER);

        assertTrue(marked());
    }

    @Test
    void on_1_21_11_a_damage_event_caused_by_anyone_else_or_nobody_does_not() {
        HitHook.install(indicator, null);

        HitHook.damageEvent(PIG, SOMEONE_ELSE, PLAYER);
        HitHook.damageEvent(PIG, -1, PLAYER);

        assertFalse(marked());
    }

    @Test
    void on_1_21_11_the_player_hurting_themselves_does_not() {
        // Fall damage names no cause, but a player's own arrow or explosion
        // names them as the cause of their own hurt.
        HitHook.install(indicator, null);

        HitHook.damageEvent(PLAYER, PLAYER, PLAYER);

        assertFalse(marked());
    }

    @Test
    void on_1_8_9_a_click_with_no_confirmation_does_not_mark() {
        HitHook.install(indicator, new RecentAttacks(now::get));

        HitHook.attacked(PIG);

        assertFalse(marked());
    }

    @Test
    void on_1_8_9_a_hurt_of_what_the_player_attacked_marks_and_of_anything_else_does_not() {
        HitHook.install(indicator, new RecentAttacks(now::get));

        HitHook.hurt(PIG);
        assertFalse(marked(), "a hurt with no attack");

        HitHook.attacked(PIG);
        now.addAndGet(150);
        HitHook.hurt(PIG);
        assertTrue(marked(), "a hurt of what the player attacked");
    }
}
