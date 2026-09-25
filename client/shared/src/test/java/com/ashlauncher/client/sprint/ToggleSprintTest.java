package com.ashlauncher.client.sprint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToggleSprintTest {

    private final FakeToggleKey key = new FakeToggleKey();
    private final ToggleSprint toggleSprint = new ToggleSprint(key, true);

    /** Whatever the game uses for the local player. Only its identity matters. */
    private Object player = new Object();

    /** One game tick with no menu open: the latch reads the key, then the game asks about sprint. */
    private boolean tick(boolean sprintKeyReallyDown) {
        toggleSprint.tick(player);
        return toggleSprint.sprintKeyDown(sprintKeyReallyDown, false);
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
        // On 1.21.11 every key repeat is counted as another press, and repeats
        // come at the operating system's rate: a delay of about half a second,
        // then around thirty a second - one or two every tick. A latch that
        // flipped on every press would flap for as long as the key was held,
        // and which way it landed would depend on how long a finger stayed down.
        key.press();
        assertTrue(tick(false));

        for (int tick = 1; tick <= 60; tick++) {
            if (tick > 10) {
                key.repeat();
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
    void a_second_press_inside_one_tick_of_a_held_key_is_taken_for_a_bounce() {
        // The one limit the game's key API imposes. A release and a fresh
        // press between two ticks, while the key was seen held at the first,
        // leave exactly what a key repeat leaves - down, one more press - and
        // nothing the game reports tells them apart. So it counts as one
        // press. That is also what a bouncing switch looks like, which is the
        // only way a person produces two presses 50ms apart.
        key.press();
        assertTrue(tick(false));

        key.release();
        key.press();
        assertTrue(tick(false), "a second press inside one tick of a held key flipped it back");
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
    void with_a_menu_open_it_reads_as_released_like_every_other_key() {
        // Opening any screen releases every key the game knows about, and the
        // game still sends the player's keys to the server every tick. A
        // latch that stayed down would send "sprint held" from inside an
        // inventory, which no vanilla client can - so it reads as released
        // there, and picks up again when the menu closes.
        key.press();
        assertTrue(tick(false));
        key.release();

        toggleSprint.tick(player);
        assertFalse(toggleSprint.sprintKeyDown(false, true), "the latch was held down inside a menu");

        assertTrue(tick(false), "closing the menu lost the latch");
    }

    @Test
    void a_new_player_starts_with_sprint_off_as_the_game_s_own_toggle_keys_do() {
        // After a death, a new world or a new server the game makes a new
        // player and resets its own toggle keys. With nothing on screen to show
        // the latch, carrying it across would leave a player sprinting who
        // has every reason to think they are not.
        key.press();
        assertTrue(tick(false));
        key.release();

        player = new Object();

        assertFalse(tick(false), "the latch outlived the player it was pressed for");
    }

    @Test
    void a_player_who_turned_it_off_gets_the_game_s_own_sprint_and_nothing_else() {
        FakeToggleKey unused = new FakeToggleKey();
        ToggleSprint off = new ToggleSprint(unused, false);

        unused.press();
        off.tick(player);

        assertFalse(off.sprintKeyDown(false, false), "a press latched sprint with the feature turned off");
        assertTrue(off.sprintKeyDown(true, false), "the game's own sprint key stopped working");
    }

    @Test
    void the_game_s_own_sprint_key_works_whatever_the_latch_says() {
        // Toggled off, holding the vanilla sprint key still sprints - toggle
        // sprint adds a way to sprint, it never takes one away.
        assertTrue(tick(true), "the vanilla sprint key did nothing");
    }
}
