package com.ashlauncher.client.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FpsMeasurementsTest {

    /** The committed results, from the shared module's own directory, where Gradle runs its tests. */
    private static final Path RESULTS = Paths.get("..", "benchmark-results");

    @Test
    void every_feature_with_a_tile_has_a_measurement_on_every_target_that_has_it() {
        for (Feature feature : Feature.values()) {
            if (feature == Feature.SETTINGS_SCREEN) {
                // No tile: nothing switches off the way to switch things.
                continue;
            }
            for (Target target : Target.values()) {
                if (feature.existsOn(target)) {
                    assertNotNull(FpsMeasurements.of(feature, target), feature.displayName() + " has no FPS"
                            + " measurement on " + target.version() + " - measure it before declaring it (#70)");
                }
            }
        }
    }

    @Test
    void no_feature_is_measured_twice_on_one_target_nor_on_a_target_without_it() {
        Set<String> seen = new HashSet<>();
        for (FpsMeasurement measurement : FpsMeasurements.all()) {
            String which = measurement.feature().id() + " on " + measurement.target().version();
            assertTrue(seen.add(which), which + " is measured twice");
            assertTrue(measurement.feature().existsOn(measurement.target()), which + " is a feature that target lacks");
        }
    }

    @Test
    void every_measurement_rests_on_committed_runs_of_its_own_target() {
        for (FpsMeasurement measurement : FpsMeasurements.all()) {
            assertEquals(3, measurement.runs().size(), measurement.feature().id() + ": one run off, two on");
            for (String run : measurement.runs()) {
                assertTrue(run.startsWith(measurement.target().version() + "-"), run + " is not a "
                        + measurement.target().version() + " run");
                assertTrue(Files.isRegularFile(RESULTS.resolve(run + ".json")), run + ".json is not committed");
            }
            assertTrue(measurement.runs().get(0).contains("-" + measurement.feature().id() + "-off-"),
                    measurement.runs().get(0) + " is not the run with " + measurement.feature().id() + " off");
        }
    }

    @Test
    void the_mark_is_level_within_three_per_cent_either_way() {
        assertEquals(FpsMark.LEVEL, FpsMark.of(0));
        assertEquals(FpsMark.LEVEL, FpsMark.of(3.0));
        assertEquals(FpsMark.LEVEL, FpsMark.of(-3.0));
        assertEquals(FpsMark.RAISES, FpsMark.of(3.01));
        assertEquals(FpsMark.LOWERS, FpsMark.of(-3.01));
    }

    @Test
    void the_change_is_on_against_off() {
        FpsMeasurement faster = new FpsMeasurement(Feature.FASTER_CLOUDS, Target.V1_8_9, 100, 112, 1);
        assertEquals(12.0, faster.changePercent(), 1e-9);
        assertEquals(FpsMark.RAISES, faster.mark());
        FpsMeasurement slower = new FpsMeasurement(Feature.CROSSHAIR, Target.V1_8_9, 100, 95, 1);
        assertEquals(FpsMark.LOWERS, slower.mark());
    }
}
