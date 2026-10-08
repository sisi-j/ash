package com.ashlauncher.client.perf;

/**
 * 1.8.9's frustum test, answered from one corner per plane instead of eight
 * (#104), with exactly the game's answer.
 *
 * <p>The game asks, for each of the frustum's six planes, whether any of the
 * box's eight corners is above it, working each corner out as
 * {@code a * x + b * y + c * z + d} in doubles. One corner is the highest
 * for every plane: the one taking the larger x where {@code a} is positive
 * and the smaller where it is negative, and the same for y and z. That holds
 * for the computed values too, not only the exact ones - every product and
 * sum is rounded, but rounding never turns a larger value into a smaller one,
 * so a corner whose every term is at least as large comes out at least as
 * large. So "any corner is above" and "that corner is above" are the same
 * answer, bit for bit. The game's own loop stops at the first corner above a
 * plane, so a box well inside costs it one corner per plane anyway; a box near
 * or beyond the frustum's edges costs it up to eight, and this one.
 *
 * <p>A plane with a coefficient that is not a finite number breaks the
 * ordering, so then the answer is left to the game.
 */
public final class FrustumPlanes {

    /** The box is inside, or crosses, every plane. */
    public static final int INSIDE = 1;
    /** The box is wholly below at least one plane. */
    public static final int OUTSIDE = 0;
    /** A plane cannot be answered this way: ask the game. */
    public static final int ASK_THE_GAME = -1;

    private FrustumPlanes() {
    }

    /** The game's answer for this box against these six planes, or {@link #ASK_THE_GAME}. */
    public static int test(float[][] planes, double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] plane = planes[i];
            float a = plane[0];
            float b = plane[1];
            float c = plane[2];
            if (!finite(a) || !finite(b) || !finite(c) || !finite(plane[3])) {
                return ASK_THE_GAME;
            }
            double x = a > 0.0F ? maxX : minX;
            double y = b > 0.0F ? maxY : minY;
            double z = c > 0.0F ? maxZ : minZ;
            // As the game's BaseFrustum.multiply works it out, term by term.
            if (!(a * x + b * y + c * z + plane[3] > 0.0)) {
                return OUTSIDE;
            }
        }
        return INSIDE;
    }

    /**
     * The game's own test, as {@code BaseFrustum.isInFrustum} has it: for
     * each plane, any of the eight corners above it. Here for the tests to
     * hold the shortcut against.
     */
    public static boolean gameTest(float[][] planes, double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] fs = planes[i];
            if (!(multiply(fs, minX, minY, minZ) > 0.0)
                    && !(multiply(fs, maxX, minY, minZ) > 0.0)
                    && !(multiply(fs, minX, maxY, minZ) > 0.0)
                    && !(multiply(fs, maxX, maxY, minZ) > 0.0)
                    && !(multiply(fs, minX, minY, maxZ) > 0.0)
                    && !(multiply(fs, maxX, minY, maxZ) > 0.0)
                    && !(multiply(fs, minX, maxY, maxZ) > 0.0)
                    && !(multiply(fs, maxX, maxY, maxZ) > 0.0)) {
                return false;
            }
        }
        return true;
    }

    private static double multiply(float[] frustum, double x, double y, double z) {
        return frustum[0] * x + frustum[1] * y + frustum[2] * z + frustum[3];
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
