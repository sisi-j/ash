package com.ashlauncher.client.sprint;

/**
 * Where each target's mixin reaches toggle sprint from.
 *
 * <p>A mixin's code is merged into the game's class and can only call out
 * statically, so the feature the client built at startup is handed over here.
 * Until it is - or if it never is - the game's own sprint key is answered
 * with the game's own answer.
 *
 * <p>In the shared module because it names nothing from either game and was
 * the same on both. The mixins that call it are not, and cannot be.
 */
public final class ToggleSprintHook {

    private static ToggleSprint toggleSprint;

    private ToggleSprintHook() {
    }

    public static void install(ToggleSprint feature) {
        toggleSprint = feature;
    }

    /**
     * Once a tick, at the top of the player's input tick.
     *
     * @param player the game's local player, for its identity only
     */
    public static void tick(Object player) {
        if (toggleSprint != null) {
            toggleSprint.tick(player);
        }
    }

    /** What the game should see for its sprint key. See {@link ToggleSprint#sprintKeyDown}. */
    public static boolean sprintKeyDown(boolean reallyDown, boolean menuOpen) {
        return toggleSprint == null ? reallyDown : toggleSprint.sprintKeyDown(reallyDown, menuOpen);
    }
}
