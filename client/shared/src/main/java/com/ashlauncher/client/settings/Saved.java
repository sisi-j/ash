package com.ashlauncher.client.settings;

/**
 * What became of a change made in game: saved, or why not.
 *
 * <p>One value per thing the player would have to do about it, so whatever
 * shows it can tell them apart. Neither message names a path or quotes an
 * exception: they are for a player reading a screen, and the file's name is
 * the only place they need.
 */
public enum Saved {

    SAVED(""),

    /**
     * The file no longer reads, so writing to it could damage what the player
     * wrote. The fix is theirs: a line with a backslash in it, usually.
     */
    FILE_UNREADABLE("Not saved: ash.properties has a line ash cannot read - often a backslash, as in a"
            + " Windows path. Fix or remove that line and change this again."),

    /** The file could not be written. Worth trying again; nothing the player wrote was lost. */
    FILE_UNWRITABLE("Not saved: ash.properties could not be written. The change lasts until the game"
            + " closes. Check the file is not read-only, then change this again.");

    private final String message;

    Saved(String message) {
        this.message = message;
    }

    /** For the player, when it was not saved; empty when it was. */
    public String message() {
        return message;
    }
}
