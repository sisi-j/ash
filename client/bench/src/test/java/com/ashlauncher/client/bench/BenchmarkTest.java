package com.ashlauncher.client.bench;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A run, driven by a clock the test sets: which frames count, and when each phase ends. */
class BenchmarkTest {

    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000_000_000L;

    /** Frames every {@code stepMs} from {@code from} up to and including {@code to}; returns the last frame's time. */
    private static long frames(Benchmark run, long from, long to, long stepMs) {
        long now = from;
        for (; now <= to; now += stepMs * MS) {
            run.frame(now);
        }
        return now - stepMs * MS;
    }

    @Test
    void nothing_counts_while_it_warms_up() {
        Benchmark run = new Benchmark(2, 1, 1, 0);

        frames(run, 0, SECOND, 10);

        assertEquals(Benchmark.Phase.WARMING_UP, run.phase());
        assertEquals(0, run.measured().size());
    }

    @Test
    void the_warm_up_waits_for_the_world_to_settle_and_stay_settled() {
        Benchmark run = new Benchmark(1, 1, 1, 0);
        run.frame(0, false);

        // Past the warm-up's own second, but still generating.
        for (long now = 10 * MS; now <= 3 * SECOND; now += 10 * MS) {
            run.frame(now, false);
        }
        assertEquals(Benchmark.Phase.WARMING_UP, run.phase());

        // Settled, unsettled again, then settled for good.
        for (long now = 3 * SECOND + 10 * MS; now <= 5 * SECOND; now += 10 * MS) {
            run.frame(now, true);
        }
        run.frame(5 * SECOND + 10 * MS, false);
        long now = 5 * SECOND + 20 * MS;
        for (; run.phase() == Benchmark.Phase.WARMING_UP; now += 10 * MS) {
            run.frame(now, true);
        }

        assertEquals(Benchmark.Phase.MEASURING, run.phase());
        assertEquals(5 * SECOND + 20 * MS + Benchmark.SETTLED_SECONDS * SECOND, now - 10 * MS,
                "the passes started the moment the world had been settled long enough");
        assertTrue(run.settledAtStart());
    }

    @Test
    void a_world_that_never_settles_is_measured_anyway_and_says_so() {
        Benchmark run = new Benchmark(1, 1, 1, 0);
        run.frame(0, false);

        for (long now = 10 * MS; run.phase() == Benchmark.Phase.WARMING_UP; now += 10 * MS) {
            run.frame(now, false);
        }

        assertEquals(Benchmark.MAX_WARM_UP_SECONDS, run.warmedUpSeconds(), 0.02);
        assertFalse(run.settledAtStart());
    }

    @Test
    void each_pass_counts_every_frame_once_and_the_run_ends_after_the_last() {
        Benchmark run = new Benchmark(1, 1, 2, 0);

        // 10 ms frames: the warm-up ends on the frame at 1 s, each pass a second after.
        frames(run, 0, 3 * SECOND, 10);

        assertEquals(Benchmark.Phase.DONE, run.phase());
        List<long[]> passes = run.measured();
        assertEquals(2, passes.size());
        assertEquals(100, passes.get(0).length, "a second of 10 ms frames");
        assertEquals(100, passes.get(1).length);
        for (long[] pass : passes) {
            for (long frame : pass) {
                assertEquals(10 * MS, frame);
            }
        }
    }

    @Test
    void a_long_frame_counts_for_all_of_its_time() {
        Benchmark run = new Benchmark(0, 1, 1, 0);
        run.frame(0);
        run.frame(0); // ends the warm-up at once

        run.frame(10 * MS);
        run.frame(260 * MS); // a 250 ms hitch
        run.frame(SECOND + 10 * MS);

        assertArrayEquals(new long[] {10 * MS, 250 * MS, 750 * MS}, run.measured().get(0));
    }

    @Test
    void after_the_passes_it_profiles_without_counting_then_finishes() {
        Benchmark run = new Benchmark(0, 1, 1, 2);
        run.frame(0);
        run.frame(0);
        long now = frames(run, 10 * MS, SECOND, 10);

        assertEquals(Benchmark.Phase.PROFILING, run.phase());
        int counted = run.measured().get(0).length;

        frames(run, now + 10 * MS, now + 2 * SECOND, 10);

        assertEquals(Benchmark.Phase.DONE, run.phase());
        assertEquals(counted, run.measured().get(0).length, "profiled frames were counted");
    }

    @Test
    void every_pass_turns_a_full_circle_from_the_same_heading() {
        Benchmark run = new Benchmark(0, 30, 2, 0);
        run.frame(0);
        run.frame(0);

        assertEquals(0F, run.yaw(0), 0.001F);
        assertEquals(90F, run.yaw(7_500 * MS), 0.001F);
        assertEquals(180F, run.yaw(15 * SECOND), 0.001F);

        frames(run, 10 * MS, 30 * SECOND + 5 * MS, 10);
        long secondPassStart = 30 * SECOND;
        assertEquals(2, run.pass());
        assertEquals(90F, run.yaw(secondPassStart + 7_500 * MS), 0.5F, "the second pass starts its turn afresh");
    }

    @Test
    void it_says_which_pass_it_is_on() {
        Benchmark run = new Benchmark(0, 1, 3, 0);
        run.frame(0);
        run.frame(0);
        assertEquals(1, run.pass());

        frames(run, 10 * MS, SECOND, 10);
        assertEquals(2, run.pass());

        frames(run, SECOND + 10 * MS, 3 * SECOND, 10);
        assertEquals(3, run.pass(), "once over, the number measured");
        assertEquals(Benchmark.Phase.DONE, run.phase());
    }
}
