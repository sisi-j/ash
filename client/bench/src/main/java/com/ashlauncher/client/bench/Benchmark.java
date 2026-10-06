package com.ashlauncher.client.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One run of the scene, driven by the time of each frame and nothing else.
 *
 * <p>A target calls {@link #frame} once a frame, from a hook that runs every
 * frame, with {@code System.nanoTime()}, and turns the camera to
 * {@link #yaw}. This decides everything else: the warm-up, when each pass
 * starts and ends, which frames count, and when the run is over. So the
 * targets are a few lines each, and what a run measures is tested here with
 * a clock a test sets.
 *
 * <p>A frame's time is the time since the frame before it. The frame that
 * ends a pass is counted in it, and the next pass starts from that frame -
 * so no frame's time is counted twice or lost between passes.
 */
public final class Benchmark {

    /** Where a run is. */
    public enum Phase {
        /** The world loads round the camera and the JIT settles; nothing counts. */
        WARMING_UP,
        /** A pass: every frame counts. */
        MEASURING,
        /** After the passes, with the game's profiler on; its frames do not count, because profiling costs time. */
        PROFILING,
        /** Over: the target writes the result and closes the game. */
        DONE
    }

    /** How long the world must have settled, together, for the warm-up to end. */
    public static final int SETTLED_SECONDS = 5;

    /** The longest a warm-up waits for the world to settle; past it, the passes start and the result says so. */
    public static final int MAX_WARM_UP_SECONDS = 180;

    private static final long SECOND = 1_000_000_000L;

    private final long warmUpNanos;
    private final long passNanos;
    private final int passes;
    private final long profileNanos;

    private Phase phase = Phase.WARMING_UP;
    private long phaseStart = -1;
    private long last = -1;
    private long settledSince = -1;
    private long warmedUpNanos = -1;
    private boolean settledAtStart;
    private long[] current = new long[1024];
    private int counted;
    private final List<long[]> measured = new ArrayList<>();

    /**
     * @param profileSeconds how long to profile after the passes; 0 for a
     *     target with no profiler to read
     */
    public Benchmark(int warmUpSeconds, int passSeconds, int passes, int profileSeconds) {
        if (passSeconds <= 0 || passes <= 0) {
            throw new IllegalArgumentException("a run needs at least one pass of some length");
        }
        this.warmUpNanos = warmUpSeconds * 1_000_000_000L;
        this.passNanos = passSeconds * 1_000_000_000L;
        this.passes = passes;
        this.profileNanos = profileSeconds * 1_000_000_000L;
    }

    /** The scene's own timings, with a profile of {@code profileSeconds}. */
    public static Benchmark ofScene(int profileSeconds) {
        return new Benchmark(Scene.WARM_UP_SECONDS, Scene.PASS_SECONDS, Scene.PASSES, profileSeconds);
    }

    /**
     * One frame, drawn at {@code now} nanoseconds, from a target that cannot
     * tell when its world has settled: the warm-up is its time alone. Returns
     * the phase the run is in after it.
     */
    public Phase frame(long now) {
        return step(now, true, false);
    }

    /**
     * One frame, drawn at {@code now}, with whether the world has settled:
     * nothing left to generate, load or build around the camera. The warm-up
     * lasts its time and then until the world has been settled for
     * {@link #SETTLED_SECONDS} together - a world still being generated
     * takes frame time from the passes, and 1.21.11's takes longer to
     * generate than any fixed warm-up can promise - but never past
     * {@link #MAX_WARM_UP_SECONDS}.
     */
    public Phase frame(long now, boolean settled) {
        return step(now, settled, true);
    }

    private Phase step(long now, boolean settled, boolean waitsToSettle) {
        if (phase == Phase.DONE) {
            return phase;
        }
        if (phaseStart < 0) {
            phaseStart = now;
            last = now;
            return phase;
        }
        if (phase == Phase.MEASURING) {
            count(now - last);
        }
        last = now;

        long elapsed = now - phaseStart;
        switch (phase) {
            case WARMING_UP:
                if (!settled) {
                    settledSince = -1;
                } else if (settledSince < 0) {
                    settledSince = now;
                }
                boolean settledLongEnough = !waitsToSettle
                        || settledSince >= 0 && now - settledSince >= SETTLED_SECONDS * SECOND;
                if ((elapsed >= warmUpNanos && settledLongEnough) || elapsed >= MAX_WARM_UP_SECONDS * SECOND) {
                    warmedUpNanos = elapsed;
                    settledAtStart = settledLongEnough;
                    start(Phase.MEASURING, now);
                }
                break;
            case MEASURING:
                if (elapsed >= passNanos) {
                    measured.add(Arrays.copyOf(current, counted));
                    counted = 0;
                    if (measured.size() < passes) {
                        start(Phase.MEASURING, now);
                    } else {
                        start(profileNanos > 0 ? Phase.PROFILING : Phase.DONE, now);
                    }
                }
                break;
            case PROFILING:
                if (elapsed >= profileNanos) {
                    start(Phase.DONE, now);
                }
                break;
            default:
                break;
        }
        return phase;
    }

    private void start(Phase next, long now) {
        phase = next;
        phaseStart = now;
    }

    private void count(long nanos) {
        if (counted == current.length) {
            current = Arrays.copyOf(current, current.length * 2);
        }
        current[counted++] = nanos;
    }

    public Phase phase() {
        return phase;
    }

    /**
     * Where the camera faces at {@code now}: a full turn each pass, from the
     * same heading at the start of each, so every pass sees the same
     * terrain at the same second. The warm-up and the profile turn the same
     * way, so the world loads all round before the first pass.
     */
    public float yaw(long now) {
        return phaseStart < 0 ? 0 : Scene.yaw(now - phaseStart, passNanos);
    }

    /** 1-based: the pass being measured, or the number measured once they are over. */
    public int pass() {
        return phase == Phase.MEASURING ? measured.size() + 1 : measured.size();
    }

    public int passes() {
        return passes;
    }

    /** How long the warm-up took, in seconds; 0 before it has ended. */
    public double warmedUpSeconds() {
        return warmedUpNanos < 0 ? 0 : warmedUpNanos / 1e9;
    }

    /**
     * Whether the world had settled when the passes started, rather than the
     * warm-up running out of time. Always true from a target that cannot
     * tell, whose fixed warm-up is taken as enough.
     */
    public boolean settledAtStart() {
        return settledAtStart;
    }

    /** Each finished pass's frame times, in nanoseconds, in the order they were drawn. */
    public List<long[]> measured() {
        return Collections.unmodifiableList(measured);
    }
}
