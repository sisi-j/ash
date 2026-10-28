package com.ashlauncher.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BetaTest {

    @Test
    void a_game_started_by_a_release_build_runs_no_beta_work() {
        // A release build's launcher says nothing at all.
        assertFalse(Beta.on(null));
    }

    @Test
    void a_game_started_by_a_test_build_runs_beta_work() {
        assertTrue(Beta.on("true"));
    }

    @Test
    void anything_but_the_launchers_own_word_is_a_release() {
        for (String other : new String[] {"", "false", "1", "yes", "TRUE", " true"}) {
            assertFalse(Beta.on(other), other);
        }
    }
}
