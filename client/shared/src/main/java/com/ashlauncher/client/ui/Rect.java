package com.ashlauncher.client.ui;

/** A rectangle on screen, in GUI units: where something is drawn, and so where a click on it lands. */
public final class Rect {

    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean overlaps(Rect other) {
        return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
    }

    public int centreX() {
        return x + width / 2;
    }

    public int centreY() {
        return y + height / 2;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + " " + width + "x" + height + ")";
    }
}
