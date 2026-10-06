package com.ashlauncher.client.snaplook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.freelook.CameraModes;
import org.junit.jupiter.api.Test;

class SnaplookTest {

    /** The game's three modes, as 1.8.9 numbers them: 0 first person, 1 behind, 2 in front. */
    private static final class Modes implements CameraModes<Integer> {
        int current = 0;

        @Override
        public Integer current() {
            return current;
        }

        @Override
        public void set(Integer mode) {
            current = mode;
        }

        @Override
        public boolean isFirstPerson(Integer mode) {
            return mode == 0;
        }

        @Override
        public Integer front() {
            return 2;
        }

        @Override
        public Integer behind() {
            return 1;
        }
    }

    private final Modes modes = new Modes();
    private boolean on = true;
    private final Snaplook<Integer> snaplook = new Snaplook<>(modes, () -> on);

    @Test
    void held_it_shows_the_front_and_letting_go_puts_first_person_back() {
        snaplook.tick(true, false);
        assertEquals(2, modes.current);

        snaplook.tick(false, false);

        assertEquals(0, modes.current);
        assertFalse(snaplook.active());
    }

    @Test
    void letting_go_puts_back_a_third_person_view_too() {
        modes.current = 1;

        snaplook.tick(true, false);
        assertEquals(2, modes.current);
        snaplook.tick(false, false);

        assertEquals(1, modes.current);
    }

    @Test
    void already_in_front_it_leaves_the_view_as_it_is() {
        modes.current = 2;

        snaplook.tick(true, false);
        snaplook.tick(false, false);

        assertEquals(2, modes.current);
    }

    @Test
    void a_view_the_player_changes_mid_hold_is_theirs_to_keep() {
        snaplook.tick(true, false);
        modes.current = 1; // F5 pressed while holding

        snaplook.tick(false, false);

        assertEquals(1, modes.current);
    }

    @Test
    void a_press_with_a_screen_open_or_switched_off_does_nothing() {
        snaplook.tick(true, true);
        assertFalse(snaplook.active());
        snaplook.tick(false, false);

        on = false;
        snaplook.tick(true, false);

        assertFalse(snaplook.active());
        assertEquals(0, modes.current);
    }

    @Test
    void switched_off_mid_hold_it_puts_the_view_back() {
        snaplook.tick(true, false);
        assertTrue(snaplook.active());

        on = false;
        snaplook.tick(true, false);

        assertFalse(snaplook.active());
        assertEquals(0, modes.current);
    }
}
