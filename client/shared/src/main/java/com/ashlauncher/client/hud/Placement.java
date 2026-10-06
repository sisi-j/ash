package com.ashlauncher.client.hud;

/**
 * Where a readout sits: an {@link Anchor}, and how far from it in GUI units.
 *
 * <p>From an edge, the distance is inwards: {@code top-right 4 4} is four in
 * from the right and four down from the top. From the middle, it is from
 * where the readout would sit centred, either way. So a readout keeps the
 * same distance from its own edge whatever the screen's size - a resolution,
 * a window or a GUI scale change - and only the middle moves with the middle.
 *
 * <p>Wherever the screen leaves it, a readout is drawn wholly on screen:
 * {@link #x} and {@link #y} bring it back within the edges, so no window ever
 * small enough loses it.
 */
public final class Placement {

    /** How far an offset can be, either way: further than any screen, and nowhere near overflowing. */
    static final int LIMIT = 100_000;

    private final Anchor anchor;
    private final int dx;
    private final int dy;

    public Placement(Anchor anchor, int dx, int dy) {
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
    }

    public Anchor anchor() {
        return anchor;
    }

    /** Left, for a readout {@code width} wide on a screen {@code screenWidth} wide, kept on it. */
    public int x(int width, int screenWidth) {
        return on(along(anchor.column, dx, width, screenWidth), width, screenWidth);
    }

    /** Top, for a readout {@code height} tall on a screen {@code screenHeight} tall, kept on it. */
    public int y(int height, int screenHeight) {
        return on(along(anchor.row, dy, height, screenHeight), height, screenHeight);
    }

    private static int along(int third, int offset, int size, int screen) {
        switch (third) {
            case 0:
                return offset;
            case 1:
                return (screen - size) / 2 + offset;
            default:
                return screen - size - offset;
        }
    }

    /** Within the screen; a readout wider than the screen keeps its start on it. */
    private static int on(int at, int size, int screen) {
        return Math.max(0, Math.min(at, screen - size));
    }

    /**
     * Where a readout dropped at ({@code x}, {@code y}) stays: brought on
     * screen, anchored to the third its middle is in, and offset so that on
     * this screen it is exactly where it was dropped.
     */
    public static Placement nearest(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int left = on(x, width, screenWidth);
        int top = on(y, height, screenHeight);
        int column = third(left + width / 2, screenWidth);
        int row = third(top + height / 2, screenHeight);
        return new Placement(Anchor.at(column, row), offset(column, left, width, screenWidth),
                offset(row, top, height, screenHeight));
    }

    private static int third(int middle, int screen) {
        if (middle * 3 < screen) {
            return 0;
        }
        return middle * 3 < screen * 2 ? 1 : 2;
    }

    private static int offset(int third, int at, int size, int screen) {
        switch (third) {
            case 0:
                return at;
            case 1:
                return at - (screen - size) / 2;
            default:
                return screen - size - at;
        }
    }

    /** As the settings file writes it: {@code top-left 4 4}. */
    public String format() {
        return anchor.id() + " " + dx + " " + dy;
    }

    /** The placement {@code raw} writes, or {@code null} when it is not one. */
    public static Placement parse(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        Anchor anchor = Anchor.of(parts[0].toLowerCase(java.util.Locale.ROOT));
        try {
            int dx = Integer.parseInt(parts[1]);
            int dy = Integer.parseInt(parts[2]);
            if (anchor == null || Math.abs(dx) > LIMIT || Math.abs(dy) > LIMIT) {
                return null;
            }
            return new Placement(anchor, dx, dy);
        } catch (NumberFormatException notWholeNumbers) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Placement)) {
            return false;
        }
        Placement that = (Placement) other;
        return anchor == that.anchor && dx == that.dx && dy == that.dy;
    }

    @Override
    public int hashCode() {
        return (anchor.hashCode() * 31 + dx) * 31 + dy;
    }

    @Override
    public String toString() {
        return format();
    }
}
