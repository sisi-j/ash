package com.ashlauncher.client.crosshair;

import com.ashlauncher.client.settings.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
     * @param arm each arm's length past the gap; for a box, half its side
     *     past the gap; for a dot, its width
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
     * The rectangles to fill, relative to the centre pixel, none overlapping
     * another: the shape is worked out as a set of pixels first, its outline
     * as the pixels around them that are not part of it, and each drawn as
     * runs along its rows. So a crosshair that is partly see-through is
     * evenly so - no pixel is filled twice, darker where two pieces met.
     *
     * <p>A line of odd thickness has the centre pixel in its middle. An even
     * one cannot - there is no middle pixel - and sits one pixel up and left
     * of it, the same on both axes, so the shape is still square.
     */
    List<Piece> pieces() {
        Set<Long> body = new HashSet<>();
        for (int[] bar : bars()) {
            for (int x = bar[0]; x < bar[0] + bar[2]; x++) {
                for (int y = bar[1]; y < bar[1] + bar[3]; y++) {
                    body.add(key(x, y));
                }
            }
        }
        List<Piece> pieces = new ArrayList<>();
        if (outlined) {
            Set<Long> outline = new HashSet<>();
            for (long pixel : body) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        long around = key(x(pixel) + dx, y(pixel) + dy);
                        if (!body.contains(around)) {
                            outline.add(around);
                        }
                    }
                }
            }
            pieces.addAll(runs(outline, (colour & 0xFF000000) | (OUTLINE_COLOUR & 0xFFFFFF)));
        }
        pieces.addAll(runs(body, colour));
        return Collections.unmodifiableList(pieces);
    }

    /** How many units across the drawn crosshair is, outline included, at scale 1. */
    public int extent() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Piece piece : pieces()) {
            min = Math.min(min, Math.min(piece.x, piece.y));
            max = Math.max(max, Math.max(piece.x + piece.width, piece.y + piece.height));
        }
        return max - min;
    }

    /** Each row's pixels as runs, left to right. */
    private static List<Piece> runs(Set<Long> pixels, int colour) {
        TreeMap<Integer, TreeSet<Integer>> rows = new TreeMap<>();
        for (long pixel : pixels) {
            rows.computeIfAbsent(y(pixel), row -> new TreeSet<>()).add(x(pixel));
        }
        List<Piece> runs = new ArrayList<>();
        for (Map.Entry<Integer, TreeSet<Integer>> row : rows.entrySet()) {
            Integer start = null;
            int previous = 0;
            for (int x : row.getValue()) {
                if (start != null && x != previous + 1) {
                    runs.add(new Piece(start, row.getKey(), previous - start + 1, 1, colour));
                    start = null;
                }
                if (start == null) {
                    start = x;
                }
                previous = x;
            }
            if (start != null) {
                runs.add(new Piece(start, row.getKey(), previous - start + 1, 1, colour));
            }
        }
        return runs;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int y(long key) {
        return (int) key;
    }

    /** The shape's lines, as {x, y, width, height}, relative to the centre pixel; they may overlap. */
    private List<int[]> bars() {
        int low = -(thickness / 2);
        List<int[]> bars = new ArrayList<>();
        switch (shape) {
            case DOT: {
                // As wide as the size says: the dot is the one shape with no
                // arms and no gap, so its size is its width.
                int from = -(arm / 2);
                bars.add(new int[] {from, from, arm, arm});
                break;
            }
            case BOX: {
                int half = gap + arm;
                int side = 2 * half + thickness;
                bars.add(new int[] {-half + low, -half + low, side, thickness});
                bars.add(new int[] {-half + low, half + low, side, thickness});
                bars.add(new int[] {-half + low, -half + low, thickness, side});
                bars.add(new int[] {half + low, -half + low, thickness, side});
                break;
            }
            default: {
                int near = low + thickness + gap;
                int far = low - gap - arm;
                bars.add(new int[] {near, low, arm, thickness});
                bars.add(new int[] {far, low, arm, thickness});
                bars.add(new int[] {low, near, thickness, arm});
                if (shape == Shape.CROSS) {
                    bars.add(new int[] {low, far, thickness, arm});
                }
                if (gap == 0) {
                    bars.add(new int[] {low, low, thickness, thickness});
                }
            }
        }
        return bars;
    }
}
