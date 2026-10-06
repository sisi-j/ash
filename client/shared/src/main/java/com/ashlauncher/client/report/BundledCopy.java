package com.ashlauncher.client.report;

/**
 * Whose copy of one of ash's bundled mods the loader ran.
 *
 * <p>The loader keeps the newest copy of a mod whose dependencies are met,
 * wherever it came from, and drops the other without a word. With the
 * player's own mods on, their newer Fabric API quietly replaces the one ash
 * pinned and tested. See {@code docs/research/0005}, A.4.
 */
public enum BundledCopy {
    /** The one ash pinned and handed to the loader. */
    ASH("ash"),
    /** One from the player's own mods folder, in place of ash's. */
    PLAYER("player");

    private final String word;

    BundledCopy(String word) {
        this.word = word;
    }

    String word() {
        return word;
    }
}
