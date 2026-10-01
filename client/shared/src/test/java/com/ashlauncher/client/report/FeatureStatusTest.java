package com.ashlauncher.client.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * What the load report says about a feature, from the two things that decide
 * it: whether the player has it on, and whether its mixins landed.
 */
class FeatureStatusTest {

    @Test
    void a_feature_that_is_on_and_landed_loaded() {
        assertEquals(FeatureStatus.LOADED, FeatureStatus.of(true, true));
    }

    @Test
    void a_feature_that_is_on_but_did_not_land_degraded() {
        assertEquals(FeatureStatus.DEGRADED, FeatureStatus.of(true, false));
    }

    @Test
    void a_feature_the_player_switched_off_is_off_whether_or_not_it_landed() {
        // The launcher's notice is about what the player wanted and did not
        // get. A feature they turned off was not wanted, so it is never a
        // problem to tell them about.
        assertEquals(FeatureStatus.OFF, FeatureStatus.of(false, true));
        assertEquals(FeatureStatus.OFF, FeatureStatus.of(false, false));
    }
}
