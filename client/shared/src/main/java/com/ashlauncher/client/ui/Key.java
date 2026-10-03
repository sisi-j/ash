package com.ashlauncher.client.ui;

/**
 * The keys ash's interface acts on, by ash's own names. Each version target
 * maps its own key codes - GLFW's on 1.21.11, LWJGL 2's on 1.8.9 - to these
 * once, so nothing in the interface knows either.
 */
public enum Key {
    /** Deletes the last character of the search. */
    BACKSPACE,
    /** Closes the panel. */
    ESCAPE
}
