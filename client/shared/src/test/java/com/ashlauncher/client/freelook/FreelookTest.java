package com.ashlauncher.client.freelook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreelookTest {

    /** The game's three modes, as 1.8.9 numbers them. */
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
    private BlockList.Server here = null;
    private final List<String> told = new ArrayList<>();
    private final Freelook<Integer> freelook = new Freelook<>(modes, () -> on, () -> here, told::add);

    @Test
    void held_it_turns_the_camera_from_where_the_player_looks_and_not_the_player() {
        freelook.tick(true, false, 90, 10);

        assertTrue(freelook.turn(100, -20));
        assertEquals(105, freelook.yaw(), 0.001);
        assertEquals(7, freelook.pitch(), 0.001);
    }

    @Test
    void the_camera_never_looks_further_up_or_down_than_the_game_lets_a_player() {
        freelook.tick(true, false, 0, 80);

        freelook.turn(0, 1000);

        assertEquals(90, freelook.pitch(), 0.001);
    }

    @Test
    void in_first_person_it_moves_behind_and_letting_go_puts_the_view_back() {
        freelook.tick(true, false, 0, 0);
        assertEquals(1, modes.current);

        freelook.tick(false, false, 0, 0);

        assertFalse(freelook.active());
        assertEquals(0, modes.current);
        assertFalse(freelook.turn(10, 10), "the mouse went to freelook after letting go");
    }

    @Test
    void a_view_the_player_chose_themselves_is_left_alone() {
        modes.current = 2;
        freelook.tick(true, false, 0, 0);
        assertEquals(2, modes.current, "the front view was changed");

        freelook.tick(true, false, 0, 0);
        modes.current = 0; // the player pressed F5 mid-hold
        freelook.tick(false, false, 0, 0);

        assertEquals(0, modes.current);
    }

    @Test
    void a_screen_opening_mid_hold_ends_it_and_it_does_not_come_back_until_pressed_again() {
        freelook.tick(true, false, 0, 0);

        freelook.tick(true, true, 0, 0);
        assertFalse(freelook.active());
        assertEquals(0, modes.current);

        freelook.tick(true, false, 0, 0);
        assertFalse(freelook.active(), "it came back with the key still held from before");
    }

    @Test
    void switched_off_the_key_does_nothing() {
        on = false;

        freelook.tick(true, false, 0, 0);

        assertFalse(freelook.active());
        assertTrue(told.isEmpty());
    }

    @Test
    void on_a_listed_server_a_press_says_why_once_and_does_nothing_else() {
        here = BlockList.match("mc.hypixel.net");

        freelook.tick(true, false, 0, 0);
        freelook.tick(true, false, 0, 0);

        assertFalse(freelook.active());
        assertEquals(0, modes.current);
        assertEquals(1, told.size());
        assertEquals("Freelook is off on Hypixel: its rules ban it.", told.get(0));
    }

    @Test
    void joining_a_listed_server_mid_hold_ends_it() {
        freelook.tick(true, false, 0, 0);

        here = BlockList.match("play.mccisland.net");
        freelook.tick(true, false, 0, 0);

        assertFalse(freelook.active());
    }
}
