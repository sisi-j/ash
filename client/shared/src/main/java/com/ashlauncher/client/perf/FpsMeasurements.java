package com.ashlauncher.client.perf;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.Target;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every feature's effect on the frame rate, on every target that has it, as
 * the frame-time measurement found it (#70). A tile's FPS mark is read from
 * here and nowhere else, so it is measured, never guessed; and a feature
 * missing from here fails {@code FpsMeasurementsTest}, so none can be declared
 * without one.
 *
 * <p><b>Where and how.</b> On the maintainer's laptop - a Ryzen 7 8845HS with
 * an RTX 4050 Laptop GPU, plugged in, on Best performance - on 8 October 2026,
 * through the benchmark's fixed scene (see {@code client/README.md}). Every
 * feature on its defaults; then each one switched off in turn, each of those
 * runs between two with everything on. The change is the off run's average
 * against the average of the two either side, and its uncertainty is the three
 * runs' together. A feature whose three runs did not agree with themselves was
 * run again, three runs at a time, in a second sitting. The results, and the
 * runs left out and why, are in {@code client/benchmark-results}, under
 * <i>FPS marks</i>.
 *
 * <p><b>What the scene cannot show.</b> It is a spectator turning on the spot,
 * with no fight, no keys pressed and no server. A feature's cost there is the
 * cost of having it on - its hooks, its drawing - not of using it; freelook,
 * for one, is measured on and idle, not turning the view.
 */
public final class FpsMeasurements {

    private static final List<FpsMeasurement> MEASURED = Collections.unmodifiableList(Arrays.asList(
            new FpsMeasurement(Feature.FPS_READOUT, Target.V1_8_9, 600.8, 596.1, 1.2,
                    "1.8.9-marks-fps-readout-off-20261008-053747",
                    "1.8.9-marks-base-20261008-053409",
                    "1.8.9-marks-base-20261008-054124"),
            new FpsMeasurement(Feature.TOGGLE_SPRINT, Target.V1_8_9, 571.7, 573.7, 1.2,
                    "1.8.9-marks-toggle-sprint-off-20261008-031317",
                    "1.8.9-marks-base-20261008-030940",
                    "1.8.9-marks-base-20261008-031655"),
            new FpsMeasurement(Feature.CROSSHAIR, Target.V1_8_9, 574.3, 566.8, 1.2,
                    "1.8.9-marks-crosshair-off-20261008-032033",
                    "1.8.9-marks-base-20261008-031655",
                    "1.8.9-marks-base-20261008-032411"),
            new FpsMeasurement(Feature.HIT_INDICATOR, Target.V1_8_9, 576.7, 576.9, 1.1,
                    "1.8.9-marks-hit-indicator-off-20261008-032748",
                    "1.8.9-marks-base-20261008-032411",
                    "1.8.9-marks-base-20261008-033127"),
            new FpsMeasurement(Feature.FREELOOK, Target.V1_8_9, 576.2, 577.9, 1.2,
                    "1.8.9-marks-freelook-off-20261008-033504",
                    "1.8.9-marks-base-20261008-033127",
                    "1.8.9-marks-base-20261008-033843"),
            new FpsMeasurement(Feature.SNAPLOOK, Target.V1_8_9, 572.6, 579.6, 1.3,
                    "1.8.9-marks-snaplook-off-20261008-034221",
                    "1.8.9-marks-base-20261008-033843",
                    "1.8.9-marks-base-20261008-034559"),
            new FpsMeasurement(Feature.PING_READOUT, Target.V1_8_9, 577.1, 582.3, 1.3,
                    "1.8.9-marks-ping-readout-off-20261008-034937",
                    "1.8.9-marks-base-20261008-034559",
                    "1.8.9-marks-base-20261008-035314"),
            new FpsMeasurement(Feature.HIT_COLOUR, Target.V1_8_9, 572.7, 576.5, 1.3,
                    "1.8.9-marks-hit-colour-off-20261008-035652",
                    "1.8.9-marks-base-20261008-035314",
                    "1.8.9-marks-base-20261008-040029"),
            new FpsMeasurement(Feature.FASTER_CLOUDS, Target.V1_8_9, 518.9, 573.0, 1.1,
                    "1.8.9-marks-faster-clouds-off-20261008-040408",
                    "1.8.9-marks-base-20261008-040029",
                    "1.8.9-marks-base-20261008-040746"),
            new FpsMeasurement(Feature.FASTER_VIEW_SCAN, Target.V1_8_9, 485.6, 573.5, 0.8,
                    "1.8.9-marks-faster-view-scan-off-20261008-041124",
                    "1.8.9-marks-base-20261008-040746",
                    "1.8.9-marks-base-20261008-041501"),
            new FpsMeasurement(Feature.FPS_READOUT, Target.V1_21_11, 712.8, 704.6, 1.3,
                    "1.21.11-marks-fps-readout-off-20261008-055305",
                    "1.21.11-marks-base-20261008-054908",
                    "1.21.11-marks-base-20261008-055721"),
            new FpsMeasurement(Feature.TOGGLE_SPRINT, Target.V1_21_11, 690.8, 704.8, 1.2,
                    "1.21.11-marks-toggle-sprint-off-20261008-060055",
                    "1.21.11-marks-base-20261008-055721",
                    "1.21.11-marks-base-20261008-060434"),
            new FpsMeasurement(Feature.CROSSHAIR, Target.V1_21_11, 702.6, 701.8, 1.1,
                    "1.21.11-marks-crosshair-off-20261008-043912",
                    "1.21.11-marks-base-20261008-043447",
                    "1.21.11-marks-base-20261008-044311"),
            new FpsMeasurement(Feature.HIT_INDICATOR, Target.V1_21_11, 699.0, 700.9, 1.0,
                    "1.21.11-marks-hit-indicator-off-20261008-044738",
                    "1.21.11-marks-base-20261008-044311",
                    "1.21.11-marks-base-20261008-045134"),
            new FpsMeasurement(Feature.FREELOOK, Target.V1_21_11, 688.7, 700.1, 1.1,
                    "1.21.11-marks-freelook-off-20261008-045511",
                    "1.21.11-marks-base-20261008-045134",
                    "1.21.11-marks-base-20261008-050005"),
            new FpsMeasurement(Feature.SNAPLOOK, Target.V1_21_11, 708.2, 702.5, 1.3,
                    "1.21.11-marks-snaplook-off-20261008-060909",
                    "1.21.11-marks-base-20261008-060434",
                    "1.21.11-marks-base-20261008-061310"),
            new FpsMeasurement(Feature.PING_READOUT, Target.V1_21_11, 700.3, 698.0, 1.1,
                    "1.21.11-marks-ping-readout-off-20261008-061706",
                    "1.21.11-marks-base-20261008-061310",
                    "1.21.11-marks-base-20261008-062102"),
            new FpsMeasurement(Feature.HIT_COLOUR, Target.V1_21_11, 713.3, 702.7, 0.8,
                    "1.21.11-marks-hit-colour-off-20261008-052031",
                    "1.21.11-marks-base-20261008-051627",
                    "1.21.11-marks-base-20261008-052517")
    ));

    private FpsMeasurements() {
    }

    /** Every measurement, for the tests. */
    public static List<FpsMeasurement> all() {
        return MEASURED;
    }

    /** The feature's measurement on this target, or null if it has none. */
    public static FpsMeasurement of(Feature feature, Target target) {
        for (FpsMeasurement measurement : MEASURED) {
            if (measurement.feature() == feature && measurement.target() == target) {
                return measurement;
            }
        }
        return null;
    }

    /** The feature's mark on this target, or null if it has no measurement - and then no mark is drawn. */
    public static FpsMark markOf(Feature feature, Target target) {
        FpsMeasurement measurement = of(feature, target);
        return measurement == null ? null : measurement.mark();
    }
}
