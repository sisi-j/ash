package com.ashlauncher.client.bench;

/**
 * The fixed scene both targets measure, so that two runs differ only in what
 * is being compared.
 *
 * <p>A world from one seed, at one place, at noon with clear weather and no
 * mobs, seen by a spectator who turns a full circle at a steady rate, in a
 * window of one size at one render distance. The turn is set by the clock,
 * not by the frame, so every run - fast machine or slow, before or after -
 * looks at the same terrain at the same second of each pass, and every pass
 * sees the whole horizon once.
 *
 * <p>The same seed makes different terrain on the two versions; the scene is
 * like for like within a version, which is the only comparison Phase 3
 * makes.
 */
public final class Scene {

    /** The world's seed. */
    public static final long SEED = 4242L;

    /** Where the camera stands: above the terrain at the world's centre, so nothing walks into it. */
    public static final double X = 0.5;
    public static final double Y = 110.0;
    public static final double Z = 0.5;

    /** Looking a little down, so the view is mostly terrain rather than sky. */
    public static final float PITCH = 20.0F;

    /** The noon the clock is held at, in ticks. */
    public static final long TIME_OF_DAY = 6000L;

    /** Chunks, on both targets: fixed rather than left to each game's default, and short, as a PvP player runs it. */
    public static final int RENDER_DISTANCE = 8;

    /** The window, set by the run's arguments; recorded with each result so a run at another size is not mistaken for one at this. */
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;
    public static final int GUI_SCALE = 2;

    /**
     * Before measuring: the world loads round the camera and the JIT settles.
     * More than a full turn, so every chunk the passes look at has been built
     * once - a first trial with less had its first pass building them.
     */
    public static final int WARM_UP_SECONDS = 40;

    /** Each pass is one full turn. */
    public static final int PASS_SECONDS = 30;

    /**
     * Passes per run. Five, because a pass on its own varies: on a laptop at
     * 600 frames a second, 1.21.11 swung 566 to 635 from one half-minute to
     * the next. The run's average is what is compared, and five passes pin it
     * down to its uncertainty - see {@code Result}.
     */
    public static final int PASSES = 5;

    private Scene() {
    }

    /**
     * Where the camera faces, in degrees, {@code nanos} into a turn of
     * {@code turnNanos}: a full circle per turn, at a steady rate.
     */
    public static float yaw(long nanos, long turnNanos) {
        double turns = (double) nanos / turnNanos;
        return (float) ((turns - Math.floor(turns)) * 360.0);
    }
}
