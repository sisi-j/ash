package com.ashlauncher.client.crosshair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A cross-shaped crosshair: four arms around the centre, and a centre square
 * when there is no gap. Sizes in the game's GUI units.
 *
 * <p>The only shape so far; the short list a player chooses from, and the
 * settings that size and colour it, are the crosshair's next ticket.
 */
public final class Cross {

    /** Around every piece when outlined, so a light crosshair stays visible against snow and sky. */
    public static final int OUTLINE_COLOUR = 0xFF000000;

    /**
     * A plus the size of the game's own: four arms of four, no gap, one
     * thick, white with a dark outline - visible on any background without
     * the game's trick of inverting what is behind it.
     */
    public static final Cross DEFAULT = new Cross(4, 0, 1, 0xFFFFFFFF, true);

    private final int arm;
    private final int gap;
    private final int thickness;
    private final int colour;
    private final boolean outlined;

    /**
     * @param arm each arm's length, past the gap
     * @param gap how far each arm starts from the centre bar's edge; 0 joins them
     * @param thickness each bar's width, centred on the centre pixel when odd
     * @param colour packed ARGB
     */
    public Cross(int arm, int gap, int thickness, int colour, boolean outlined) {
        this.arm = arm;
        this.gap = gap;
        this.thickness = thickness;
        this.colour = colour;
        this.outlined = outlined;
    }

    public int arm() {
        return arm;
    }

    /** One rectangle to fill, relative to the centre pixel. */
    static final class Piece {
        final int x;
        final int y;
        final int width;
        final int height;
        final int colour;

        Piece(int x, int y, int width, int height, int colour) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.colour = colour;
        }
    }

    /**
     * The rectangles to fill, in order, relative to the centre pixel: every
     * outline first, then every bar, so no outline lies across a bar.
     *
     * <p>A bar of odd thickness has the centre pixel in its middle. An even
     * one cannot - there is no middle pixel - and sits one pixel up and left
     * of it, the same on both axes, so the cross is still square.
     */
    List<Piece> pieces() {
        int low = -(thickness / 2);
        int near = low + thickness + gap;
        int far = low - gap - arm;
        List<Piece> bars = new ArrayList<>();
        bars.add(new Piece(near, low, arm, thickness, colour));
        bars.add(new Piece(far, low, arm, thickness, colour));
        bars.add(new Piece(low, near, thickness, arm, colour));
        bars.add(new Piece(low, far, thickness, arm, colour));
        if (gap == 0) {
            bars.add(new Piece(low, low, thickness, thickness, colour));
        }

        List<Piece> pieces = new ArrayList<>();
        if (outlined) {
            for (Piece bar : bars) {
                pieces.add(new Piece(bar.x - 1, bar.y - 1, bar.width + 2, bar.height + 2, OUTLINE_COLOUR));
            }
        }
        pieces.addAll(bars);
        return Collections.unmodifiableList(pieces);
    }
}
