package com.ashlauncher.client.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadReportTest {

    /**
     * The contract. The launcher's tests parse this same file, so the client
     * and the launcher cannot each pass while disagreeing about the format.
     * Relative to this module, which is where Gradle runs its tests.
     */
    private static final Path CONTRACT = Paths.get("../../launcher/core/tests/fixtures/load-report.json");

    @TempDir
    Path gameDir;

    private static String withUnixLineEndings(String text) {
        // Git may check the contract out with Windows line endings; the
        // client writes \n everywhere. The only difference forgiven.
        return text.replace("\r\n", "\n");
    }

    @Test
    void it_writes_exactly_what_the_launcher_reads() throws IOException {
        new LoadReport("0.1.0")
                .with(Feature.FPS_READOUT, FeatureState.LOADED)
                .with(Feature.TOGGLE_SPRINT, FeatureState.DEGRADED)
                .writeTo(gameDir);

        String written = Files.readString(gameDir.resolve("ash").resolve("load-report.json"));
        assertEquals(withUnixLineEndings(Files.readString(CONTRACT)), written);
    }

    @Test
    void a_value_with_a_quote_in_it_is_still_json_the_launcher_can_read() throws IOException {
        // The client version comes from the build, not from this class, and a
        // local build can call itself anything.
        new LoadReport("0.1.0+\"local\"\\build").with(Feature.FPS_READOUT, FeatureState.LOADED).writeTo(gameDir);

        String written = Files.readString(gameDir.resolve("ash").resolve("load-report.json"));
        assertTrue(written.contains("\"client\": \"0.1.0+\\\"local\\\"\\\\build\""), written);
    }

    @Test
    void each_session_replaces_the_last_one_s_report() throws IOException {
        // Which is what clears the launcher's notice once a feature loads
        // again: the notice is read from the last report, and nothing else.
        new LoadReport("0.1.0").with(Feature.TOGGLE_SPRINT, FeatureState.DEGRADED).writeTo(gameDir);
        new LoadReport("0.1.1").with(Feature.TOGGLE_SPRINT, FeatureState.LOADED).writeTo(gameDir);

        String written = Files.readString(gameDir.resolve("ash").resolve("load-report.json"));
        assertTrue(written.contains("\"status\": \"loaded\""), written);
        assertFalse(written.contains("degraded"), "the last session's report survived:\n" + written);
    }
}
