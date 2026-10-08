package com.ashlauncher.client.perf;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.Target;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One feature's effect on the frame rate on one target, as the frame-time
 * measurement found it: the run with it off, and the runs either side of it
 * with everything on, from {@code client/benchmark-results}.
 */
public final class FpsMeasurement {

    private final Feature feature;
    private final Target target;
    private final double offFps;
    private final double onFps;
    private final double uncertaintyPercent;
    private final List<String> runs;

    /**
     * @param offFps the average frame rate with the feature off
     * @param onFps the average of the runs with it on
     * @param uncertaintyPercent the change's uncertainty, from the runs'
     * @param runs the result files it rests on, without {@code .json}: the run
     *     with it off first
     */
    FpsMeasurement(Feature feature, Target target, double offFps, double onFps, double uncertaintyPercent,
            String... runs) {
        this.feature = feature;
        this.target = target;
        this.offFps = offFps;
        this.onFps = onFps;
        this.uncertaintyPercent = uncertaintyPercent;
        this.runs = Collections.unmodifiableList(Arrays.asList(runs));
    }

    public Feature feature() {
        return feature;
    }

    public Target target() {
        return target;
    }

    /** How much faster the game runs with the feature on than off, in per cent; negative for slower. */
    public double changePercent() {
        return (onFps - offFps) / offFps * 100.0;
    }

    public double uncertaintyPercent() {
        return uncertaintyPercent;
    }

    public FpsMark mark() {
        return FpsMark.of(changePercent());
    }

    public List<String> runs() {
        return runs;
    }
}
