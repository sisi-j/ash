package com.ashlauncher.client.hud;

import java.util.Locale;

/**
 * The corner, edge or middle of the screen a readout keeps its distance from:
 * the screen split in thirds each way, and named for the third it is in.
 *
 * <p>A readout in the right third keeps its distance from the right edge, so
 * a wider window moves it with that edge, and a number that grows a digit
 * grows leftwards, away from the edge, rather than off the screen. One in the
 * middle third keeps its distance from the middle.
 */
public enum Anchor {
    TOP_LEFT(0, 0, "Top left"),
    TOP(1, 0, "Top"),
    TOP_RIGHT(2, 0, "Top right"),
    LEFT(0, 1, "Left"),
    CENTRE(1, 1, "Centre"),
    RIGHT(2, 1, "Right"),
    BOTTOM_LEFT(0, 2, "Bottom left"),
    BOTTOM(1, 2, "Bottom"),
    BOTTOM_RIGHT(2, 2, "Bottom right");

    /** 0, 1 or 2: the left, middle or right third. */
    final int column;
    /** 0, 1 or 2: the top, middle or bottom third. */
    final int row;
    private final String label;

    Anchor(int column, int row, String label) {
        this.column = column;
        this.row = row;
        this.label = label;
    }

    /** As the settings file writes it: {@code top-left}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** As the Edit HUD tag shows it: "Top left". */
    public String label() {
        return label;
    }

    /** The anchor with this id, or {@code null}. */
    public static Anchor of(String id) {
        for (Anchor anchor : values()) {
            if (anchor.id().equals(id)) {
                return anchor;
            }
        }
        return null;
    }

    static Anchor at(int column, int row) {
        for (Anchor anchor : values()) {
            if (anchor.column == column && anchor.row == row) {
                return anchor;
            }
        }
        throw new IllegalArgumentException(column + "," + row);
    }
}
