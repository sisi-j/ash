package com.ashlauncher.client.sprint;

/**
 * The key the player bound to toggle sprint, with the game on the far side.
 *
 * <p>The binding helper that registers it has a different class name on each
 * target, and so does the key type it returns - which is why nothing here can
 * name either, and why each target answers these two questions in a line.
 */
public interface ToggleKey {

    /** Whether the key is down at this moment. */
    boolean held();

    /**
     * How many times the key went down since the last call, which this resets.
     *
     * <p>Counted the way the game counts them, which on 1.21.11 includes a key
     * repeat for every half-second or so a key is held.
     */
    int takePresses();
}
