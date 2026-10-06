package com.ashlauncher.client.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SummaryTest {

    private static final long MS = 1_000_000L;

    private static long[] steady(int count, long ms) {
        long[] frames = new long[count];
        Arrays.fill(frames, ms * MS);
        return frames;
    }

    @Test
    void steady_frames_are_their_own_average_and_their_own_1_percent_low() {
        Summary summary = Summary.of(steady(500, 4));

        assertEquals(500, summary.frames());
        assertEquals(2.0, summary.seconds(), 1e-9);
        assertEquals(250.0, summary.averageFps(), 1e-9);
        assertEquals(4.0, summary.averageMs(), 1e-9);
        assertEquals(250.0, summary.onePercentLowFps(), 1e-9);
    }

    @Test
    void the_average_is_frames_over_time_so_a_long_frame_counts_for_its_time() {
        // 99 frames of 5 ms and one of 505: a second, 100 frames. The mean of
        // each frame's own rate would say about 198 FPS; the player saw 100.
        long[] frames = steady(100, 5);
        frames[50] = 505 * MS;

        assertEquals(100.0, Summary.of(frames).averageFps(), 1e-9);
    }

    @Test
    void the_1_percent_low_is_the_rate_of_the_slowest_hundredth() {
        // 1000 frames: the slowest ten are 20 ms, so the 1% low is 50 FPS
        // whatever the other 990 did.
        long[] frames = steady(1000, 4);
        for (int i = 0; i < 10; i++) {
            frames[i * 97] = 20 * MS;
        }

        Summary summary = Summary.of(frames);

        assertEquals(50.0, summary.onePercentLowFps(), 1e-9);
        assertEquals(20.0, summary.worstMs(), 1e-9);
    }

    @Test
    void under_a_hundred_frames_the_slowest_frame_is_the_1_percent_low() {
        long[] frames = steady(40, 10);
        frames[7] = 40 * MS;

        assertEquals(25.0, Summary.of(frames).onePercentLowFps(), 1e-9);
    }

    @Test
    void several_passes_summarise_as_one() {
        Summary all = Summary.ofAll(Arrays.asList(steady(100, 10), steady(100, 5)));

        assertEquals(200, all.frames());
        assertEquals(1.5, all.seconds(), 1e-9);
    }

    @Test
    void there_is_nothing_to_summarise_without_frames() {
        assertThrows(IllegalArgumentException.class, () -> Summary.of(new long[0]));
    }
}
