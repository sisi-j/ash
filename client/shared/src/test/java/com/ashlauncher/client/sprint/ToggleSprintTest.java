package com.ashlauncher.client.sprint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToggleSprintTest {

    private final FakeToggleKey key = new FakeToggleKey();
    private final ToggleSprint toggleSprint = new ToggleSprint(key, true);

    /** One game tick: the latch reads the key, then the game asks about sprint. */
    private boolean tick(boolean sprintKeyReallyDown) {
        toggleSprint.tick();
        return toggleSprint.sprintKeyDown(sprintKeyReallyDown);
    }

    @Test
    void a_press_turns_sprint_on_and_the_next_press_turns_it_off() {
        assertFalse(tick(false), "sprinting before anything was pressed");

        key.press();
        assertTrue(tick(false), "the first press did not turn sprint on");
        key.release();
        assertTrue(tick(false), "letting go of the key turned sprint off");

        key.press();
        assertFalse(tick(false), "the second press did not turn sprint off");
    }

    @Test
    void holding_the_key_while_the_game_repeats_it_leaves_sprint_on() {
        // On 1.21.11 every key repeat is counted as another press, so a latch
        // that flipped on every press would flap on and off for as long as
        // the key was held - and which way it landed would depend on how long
        // the player's finger happened to stay down.
        key.press();
        assertTrue(tick(false));

        for (int tick = 1; tick <= 60; tick++) {
            if (tick % 10 == 0) {
                key.repeat();
            }
            assertTrue(tick(false), "sprint flipped off at tick " + tick + " of a single held press");
        }
    }

    @Test
    void a_tap_shorter_than_a_tick_still_counts() {
        // A tick is 50ms. A key that goes down and up inside one is never seen
        // held, and a rapid-trigger keyboard makes that an ordinary press
        // rather than a freak one - a latch that only watched the key being
        // held would miss it, and the player would not be sprinting when they
        // think they are.
        key.tap();

        assertTrue(tick(false), "a press shorter than a tick was missed");
    }

    @Test
    void rebinding_leaves_sprint_as_it_was_and_the_new_key_works_straight_away() {
        key.press();
        assertTrue(tick(false));

        key.rebindToAKeyThatIsUp();
        assertTrue(tick(false), "rebinding the key turned sprint off");

        key.press();
        assertFalse(tick(false), "the newly bound key's first press was ignored");
    }

    @Test
    void rebinding_to_a_key_already_down_does_not_flip_it() {
        // The key that was pressed to choose the new binding can still be
        // down when the game starts reporting it. Seeing it held is not seeing
        // it pressed.
        key.rebindToAKeyThatIsDown();

        assertFalse(tick(false), "rebinding to a held key turned sprint on");
    }

    @Test
    void a_player_who_turned_it_off_gets_the_game_s_own_sprint_and_nothing_else() {
        FakeToggleKey unused = new FakeToggleKey();
        ToggleSprint off = new ToggleSprint(unused, false);

        unused.press();
        off.tick();

        assertFalse(off.sprintKeyDown(false), "a press latched sprint with the feature turned off");
        assertTrue(off.sprintKeyDown(true), "the game's own sprint key stopped working");
    }

    @Test
    void the_game_s_own_sprint_key_works_whatever_the_latch_says() {
        // Toggled off, holding the vanilla sprint key still sprints - toggle
        // sprint adds a way to sprint, it never takes one away.
        assertTrue(tick(true), "the vanilla sprint key did nothing");
    }
}
