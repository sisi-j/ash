package com.ashlauncher.client.crosshair;

import com.ashlauncher.client.settings.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A crosshair as the player set it up: its shape, how long its arms are, the
 * gap at the centre, how thick its lines are, its colour, and whether it is
 * outlined. Sizes in the game's GUI units.
 */
public final class Cross {

    /** Around every piece when outlined - at the crosshair's own opacity - so a light one shows on snow and sky. */
    public static final int OUTLINE_COLOUR = 0xFF000000;

    /**
     * A plus the size of the game's own: four arms of four, no gap, one
     * thick, white with a dark outline - visible on any background without
     * the game's trick of inverting what is behind it.
     */
    public static final Cross DEFAULT = new Cross(Shape.CROSS, 4, 0, 1, 0xFFFFFFFF, true);

    /** Something rectangles can be filled on: a HUD, a screen, a test's record of both. */
    public interface Filler {
        void fill(int x, int y, int width, int height, int colour);
    }

    private final Shape shape;
    private final int arm;
    private final int gap;
    private final int thickness;
    private final int colour;
    private final boolean outlined;

    /**
     * @param arm each arm's length past the gap; for a box, half its side past the gap
     * @param gap how far the arms start from the centre bar's edge; 0 joins them
     * @param thickness each line's width, centred on the centre pixel when odd
     * @param colour packed ARGB
     */
    public Cross(Shape shape, int arm, int gap, int thickness, int colour, boolean outlined) {
        this.shape = shape;
        this.arm = arm;
        this.gap = gap;
        this.thickness = thickness;
        this.colour = colour;
        this.outlined = outlined;
    }

    /** The crosshair the player's settings describe, as they are this moment. */
    public static Cross of(Settings settings) {
        return new Cross(Shape.of(settings.get(Settings.CROSSHAIR_SHAPE)), settings.get(Settings.CROSSHAIR_SIZE),
                settings.get(Settings.CROSSHAIR_GAP), settings.get(Settings.CROSSHAIR_THICKNESS),
                settings.get(Settings.CROSSHAIR_COLOUR), settings.get(Settings.CROSSHAIR_OUTLINE));
    }

    public int arm() {
        return arm;
    }

    /**
     * Fills the crosshair centred on ({@code centreX}, {@code centreY}),
     * every piece {@code scale} times its size - 1 in game, more in the
     * settings screen's preview.
     */
    public void drawOnto(Filler filler, int centreX, int centreY, int scale) {
        for (Piece piece : pieces()) {
            filler.fill(centreX + piece.x * scale, centreY + piece.y * scale, piece.width * scale, piece.height * scale,
                    piece.colour);
        }
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
     * of it, the same on both axes, so the shape is still square.
     */
    List<Piece> pieces() {
        List<Piece> bars = bars();
        List<Piece> pieces = new ArrayList<>();
        if (outlined) {
            int outline = (colour & 0xFF000000) | (OUTLINE_COLOUR & 0xFFFFFF);
            for (Piece bar : bars) {
                pieces.add(new Piece(bar.x - 1, bar.y - 1, bar.width + 2, bar.height + 2, outline));
            }
        }
        pieces.addAll(bars);
        return Collections.unmodifiableList(pieces);
    }

    private List<Piece> bars() {
        int low = -(thickness / 2);
        List<Piece> bars = new ArrayList<>();
        switch (shape) {
            case DOT: {
                int side = 2 * thickness + 1;
                int from = -(side / 2);
                bars.add(new Piece(from, from, side, side, colour));
                break;
            }
            case BOX: {
                int half = gap + arm;
                int side = 2 * half + thickness;
                bars.add(new Piece(-half + low, -half + low, side, thickness, colour));
                bars.add(new Piece(-half + low, half + low, side, thickness, colour));
                bars.add(new Piece(-half + low, -half + low, thickness, side, colour));
                bars.add(new Piece(half + low, -half + low, thickness, side, colour));
                break;
            }
            default: {
                int near = low + thickness + gap;
                int far = low - gap - arm;
                bars.add(new Piece(near, low, arm, thickness, colour));
                bars.add(new Piece(far, low, arm, thickness, colour));
                bars.add(new Piece(low, near, thickness, arm, colour));
                if (shape == Shape.CROSS) {
                    bars.add(new Piece(low, far, thickness, arm, colour));
                }
                if (gap == 0) {
                    bars.add(new Piece(low, low, thickness, thickness, colour));
                }
            }
        }
        return bars;
    }
}
