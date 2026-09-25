package com.ashlauncher.client.sprint;

/**
 * The key the player bound to toggle sprint, with the game on the far side.
 *
 * <p>The binding helper that registers it is {@code KeyBindingHelper} on both
 * targets but in two different packages - Fabric API's and Legacy Fabric's -
 * and the key type it returns is {@code KeyMapping} on one and
 * {@code KeyBinding} on the other. So nothing here can name any of them, and
 * each target answers these two questions in a line.
 */
public interface ToggleKey {

    /** Whether the key is down at this moment. */
    boolean held();

    /**
     * How many times the key went down since the last call, which this resets.
     *
     * <p>Counted the way the game counts them, which on 1.21.11 includes every
     * key repeat - one or two a tick once a key has been held about half a
     * second.
     */
    int takePresses();
}
