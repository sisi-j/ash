package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.sprint.ToggleSprint;

/**
 * Where the mixin reaches toggle sprint from.
 *
 * <p>A mixin's code is merged into the game's class and can only call out
 * statically, so the feature the client built at startup is handed over here.
 * Until it is - or if it never is - the game's own sprint key is answered
 * with the game's own answer.
 */
public final class ToggleSprintHook {

    private static ToggleSprint toggleSprint;

    private ToggleSprintHook() {
    }

    static void install(ToggleSprint feature) {
        toggleSprint = feature;
    }

    /** Once a tick, at the top of the player's movement tick. */
    public static void tick() {
        if (toggleSprint != null) {
            toggleSprint.tick();
        }
    }

    /** What the game should see for its sprint key, given what it really is. */
    public static boolean sprintKeyDown(boolean reallyDown) {
        return toggleSprint == null ? reallyDown : toggleSprint.sprintKeyDown(reallyDown);
    }
}
