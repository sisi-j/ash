package com.ashlauncher.client.bench;

import java.util.Arrays;

/**
 * What a run of frames comes to: how many, the average frame rate, and the
 * 1% low - the frame rate of the slowest hundredth of frames, which is the
 * stutter a player feels and an average hides.
 *
 * <p>The average frame rate is frames over time, not the average of each
 * frame's own rate: a few long frames have to count for the time they took.
 */
public final class Summary {

    private final int frames;
    private final double seconds;
    private final double onePercentLowMs;
    private final double worstMs;

    private Summary(int frames, double seconds, double onePercentLowMs, double worstMs) {
        this.frames = frames;
        this.seconds = seconds;
        this.onePercentLowMs = onePercentLowMs;
        this.worstMs = worstMs;
    }

    /** @param frameNanos each frame's time, in nanoseconds; at least one */
    public static Summary of(long[] frameNanos) {
        if (frameNanos.length == 0) {
            throw new IllegalArgumentException("no frames to summarise");
        }
        long[] sorted = frameNanos.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long nanos : sorted) {
            total += nanos;
        }
        // The slowest hundredth, never less than one frame.
        int slowest = Math.max(1, (int) Math.ceil(sorted.length / 100.0));
        long slowTotal = 0;
        for (int i = sorted.length - slowest; i < sorted.length; i++) {
            slowTotal += sorted[i];
        }
        return new Summary(sorted.length, total / 1e9, slowTotal / 1e6 / slowest, sorted[sorted.length - 1] / 1e6);
    }

    /** The frames from several passes, as one. */
    public static Summary ofAll(Iterable<long[]> passes) {
        int count = 0;
        for (long[] pass : passes) {
            count += pass.length;
        }
        long[] all = new long[count];
        int at = 0;
        for (long[] pass : passes) {
            System.arraycopy(pass, 0, all, at, pass.length);
            at += pass.length;
        }
        return of(all);
    }

    public int frames() {
        return frames;
    }

    public double seconds() {
        return seconds;
    }

    public double averageFps() {
        return frames / seconds;
    }

    public double averageMs() {
        return seconds * 1000 / frames;
    }

    /** The frame rate the slowest 1% of frames ran at. */
    public double onePercentLowFps() {
        return 1000 / onePercentLowMs;
    }

    /** The longest frame, in milliseconds. */
    public double worstMs() {
        return worstMs;
    }
}
