package com.ashlauncher.client.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultTest {

    private static final long MS = 1_000_000L;

    @TempDir
    Path dir;

    private static long[] steady(int count, double ms) {
        long[] frames = new long[count];
        Arrays.fill(frames, Math.round(ms * MS));
        return frames;
    }

    private static Result result(List<long[]> passes) {
        Map<String, String> machine = new LinkedHashMap<>();
        machine.put("gpu", "Test \"GPU\"");
        Map<String, String> settings = Collections.singletonMap("fps-readout.enabled", "true");
        return new Result("1.8.9", "baseline", "2026-10-06T15:42:10Z", machine, settings, passes,
                Collections.singletonList(new Result.Section("root.gameRenderer.level", 61.5)));
    }

    @Test
    void passes_that_agree_are_comparable_and_say_how_closely() {
        // 250, 247.5 and 252.5 FPS: a sample deviation of 2.5, 1% of 250.
        Result result = result(Arrays.asList(steady(1000, 1000.0 / 250), steady(1000, 1000.0 / 247.5),
                steady(1000, 1000.0 / 252.5)));

        assertEquals(1.0, result.averageSpreadPercent(), 0.01);
        assertTrue(result.comparable());
    }

    @Test
    void passes_that_disagree_by_more_than_3_percent_are_not() {
        Result result = result(Arrays.asList(steady(1000, 4), steady(1000, 5)));

        assertFalse(result.comparable());
        assertTrue(result.oneLine().contains("too far apart to compare"), result.oneLine());
    }

    @Test
    void one_pass_has_no_spread() {
        assertEquals(0.0, result(Collections.singletonList(steady(100, 4))).averageSpreadPercent());
    }

    @Test
    void the_json_has_the_scene_the_machine_the_settings_the_passes_and_the_profile() {
        String json = result(Arrays.asList(steady(500, 4), steady(500, 4))).json();

        assertTrue(json.contains("\"target\": \"1.8.9\""), json);
        assertTrue(json.contains("\"seed\": " + Scene.SEED), json);
        assertTrue(json.contains("\"gpu\": \"Test \\\"GPU\\\"\""), "quotes escaped: " + json);
        assertTrue(json.contains("\"fps-readout.enabled\": \"true\""), json);
        assertTrue(json.contains("\"overall\": {\"frames\": 1000, \"seconds\": 4.00, \"averageFps\": 250.00"), json);
        assertTrue(json.contains("\"comparable\": true"), json);
        assertTrue(json.contains("{\"section\": \"root.gameRenderer.level\", \"percentOfFrame\": 61.50}"), json);
    }

    @Test
    void the_csv_has_every_frame_by_pass() {
        String csv = result(Arrays.asList(steady(2, 4), steady(1, 16.5))).framesCsv();

        assertEquals("pass,frame,ms\n1,1,4.000\n1,2,4.000\n2,1,16.500\n", csv);
    }

    @Test
    void it_writes_both_files_named_for_the_target_label_and_time() throws Exception {
        Path json = result(Collections.singletonList(steady(10, 4))).write(dir.resolve("out"), "20261006-154210");

        assertEquals("1.8.9-baseline-20261006-154210.json", json.getFileName().toString());
        assertTrue(Files.exists(dir.resolve("out/1.8.9-baseline-20261006-154210-frames.csv")));
    }

    @Test
    void a_label_is_made_safe_for_a_file_name() {
        assertEquals("with-lithium", Result.safe("with lithium"));
        assertEquals("a-b", Result.safe("a/b"));
        assertEquals("run", Result.safe(""));
    }

    @Test
    void a_control_character_is_escaped_in_json() {
        assertEquals("\"a\\u000ab\"", Result.string("a\nb"));
    }
}
