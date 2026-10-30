package com.ashlauncher.client.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where a HUD item being dragged in Edit HUD snaps, and the guide lines that
 * show what it snapped to (spec 0007, The HUD editor). All in GUI units.
 *
 * <p>Within {@link #DISTANCE} of a line, it moves onto it: its edges onto the
 * screen's edges, its middle onto the screen's centre lines, and its edges
 * and middle onto the edges and middle of each other item shown - so a column
 * of items lines up, and items stack edge to edge. Across and down are
 * snapped apart, each to its nearest line.
 */
public final class Snap {

    /** How near a line an item has to come to snap to it, in GUI units. */
    public static final int DISTANCE = 4;

    /** An item's box on screen, in GUI units. */
    public static final class Box {
        final int x;
        final int y;
        final int width;
        final int height;

        public Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /** A line an item snapped to: up the screen at an x, or across it at a y. */
    public static final class Guide {
        private final boolean vertical;
        private final int at;

        private Guide(boolean vertical, int at) {
            this.vertical = vertical;
            this.at = at;
        }

        public static Guide vertical(int x) {
            return new Guide(true, x);
        }

        public static Guide horizontal(int y) {
            return new Guide(false, y);
        }

        /** Whether it runs up the screen, at {@link #at} across; otherwise across it, at {@link #at} down. */
        public boolean isVertical() {
            return vertical;
        }

        public int at() {
            return at;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Guide && ((Guide) other).vertical == vertical && ((Guide) other).at == at;
        }

        @Override
        public int hashCode() {
            return (vertical ? 31 : 0) + at;
        }

        @Override
        public String toString() {
            return (vertical ? "x = " : "y = ") + at;
        }
    }

    private final int x;
    private final int y;
    private final List<Guide> guides;

    private Snap(int x, int y, List<Guide> guides) {
        this.x = x;
        this.y = y;
        this.guides = Collections.unmodifiableList(guides);
    }

    /**
     * Where an item {@code width} by {@code height}, dragged to ({@code x},
     * {@code y}), snaps on a screen this size among the other items shown.
     */
    public static Snap of(int x, int y, int width, int height, int screenWidth, int screenHeight, List<Box> others) {
        List<Guide> guides = new ArrayList<>();
        int[] across = nearest(x, width, screenWidth, others, true);
        int[] down = nearest(y, height, screenHeight, others, false);
        if (across != null) {
            x += across[0];
            guides.add(Guide.vertical(across[1]));
        }
        if (down != null) {
            y += down[0];
            guides.add(Guide.horizontal(down[1]));
        }
        return new Snap(x, y, guides);
    }

    /**
     * The nearest snap along one direction - how far to move, and the line -
     * or {@code null} when no line is within {@link #DISTANCE}.
     */
    private static int[] nearest(int start, int size, int screen, List<Box> others, boolean across) {
        int[] best = null;
        int end = start + size;
        int middle = start + size / 2;
        best = closer(best, start, 0);
        best = closer(best, end, screen);
        best = closer(best, middle, screen / 2);
        for (Box other : others) {
            int otherStart = across ? other.x : other.y;
            int otherSize = across ? other.width : other.height;
            int otherEnd = otherStart + otherSize;
            for (int line : new int[] {otherStart, otherEnd}) {
                best = closer(best, start, line);
                best = closer(best, end, line);
            }
            best = closer(best, middle, otherStart + otherSize / 2);
        }
        return best;
    }

    /** {@code best}, or the move from {@code edge} onto {@code line} if that is within reach and nearer. */
    private static int[] closer(int[] best, int edge, int line) {
        int move = line - edge;
        if (Math.abs(move) > DISTANCE || (best != null && Math.abs(move) >= Math.abs(best[0]))) {
            return best;
        }
        return new int[] {move, line};
    }

    /** Its left edge, snapped. */
    public int x() {
        return x;
    }

    /** Its top, snapped. */
    public int y() {
        return y;
    }

    /** What it snapped to, across then down; empty when nothing was within reach. */
    public List<Guide> guides() {
        return guides;
    }
}
