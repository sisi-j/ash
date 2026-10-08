package com.ashlauncher.client.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class FrustumPlanesTest {

    @Test
    void answers_as_the_game_does_for_a_million_boxes_against_random_frustums() {
        SplittableRandom random = new SplittableRandom(45104);
        int inside = 0;
        int outside = 0;
        for (int round = 0; round < 1_000_000; round++) {
            float[][] planes = randomPlanes(random);
            double[] box = randomBox(random);
            boolean game = FrustumPlanes.gameTest(planes, box[0], box[1], box[2], box[3], box[4], box[5]);
            int ash = FrustumPlanes.test(planes, box[0], box[1], box[2], box[3], box[4], box[5]);
            assertEquals(game ? FrustumPlanes.INSIDE : FrustumPlanes.OUTSIDE, ash, "round " + round);
            if (game) {
                inside++;
            } else {
                outside++;
            }
        }
        // Both answers must have come up often, or the test proved little.
        assertTrue(inside > 50_000 && outside > 50_000, inside + " inside, " + outside + " outside");
    }

    @Test
    void answers_as_the_game_does_on_the_edge_where_a_corner_lies_on_a_plane() {
        // Planes through a corner exactly, with zero and negative zero
        // coefficients: where the sign of a zero or a tie could tell.
        float[][] through = {
            {1, 0, 0, 0}, {-1, 0, 0, 16}, {0, 1, 0, 0}, {0, -1, 0, 16}, {0, 0, 1, 0}, {0, 0, -1, 16}};
        assertAgree(through, 0, 0, 0, 16, 16, 16);
        assertAgree(through, -16, 0, 0, 0, 16, 16);
        assertAgree(through, 16, 0, 0, 32, 16, 16);
        float[][] zeros = {
            {-0.0F, 0, 1, 0}, {0, -0.0F, 1, 0}, {0, 0, -0.0F, 0}, {0, 0, 0, 0}, {-0.0F, -0.0F, -0.0F, -0.0F},
            {0.5F, -0.5F, 0, 0}};
        assertAgree(zeros, -8, -8, -8, 8, 8, 8);
        assertAgree(zeros, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void leaves_a_plane_that_is_not_a_finite_number_to_the_game() {
        float[][] planes = randomPlanes(new SplittableRandom(1));
        planes[0][1] = Float.NaN;
        assertEquals(FrustumPlanes.ASK_THE_GAME, FrustumPlanes.test(planes, 0, 0, 0, 16, 16, 16));
        planes[0][1] = Float.POSITIVE_INFINITY;
        assertEquals(FrustumPlanes.ASK_THE_GAME, FrustumPlanes.test(planes, 0, 0, 0, 16, 16, 16));
    }

    private static void assertAgree(float[][] planes, double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        boolean game = FrustumPlanes.gameTest(planes, minX, minY, minZ, maxX, maxY, maxZ);
        assertEquals(game ? FrustumPlanes.INSIDE : FrustumPlanes.OUTSIDE,
                FrustumPlanes.test(planes, minX, minY, minZ, maxX, maxY, maxZ));
    }

    /** Six normalised planes, as the game's frustum has, some with a zero coefficient. */
    private static float[][] randomPlanes(SplittableRandom random) {
        float[][] planes = new float[6][4];
        for (float[] plane : planes) {
            float a = (float) (random.nextDouble() * 2 - 1);
            float b = (float) (random.nextDouble() * 2 - 1);
            float c = (float) (random.nextDouble() * 2 - 1);
            if (random.nextInt(8) == 0) {
                a = random.nextBoolean() ? 0.0F : -0.0F;
            }
            float length = (float) Math.sqrt(a * a + b * b + c * c);
            if (length == 0) {
                length = 1;
            }
            plane[0] = a / length;
            plane[1] = b / length;
            plane[2] = c / length;
            plane[3] = (float) (random.nextDouble() * 400 - 100);
        }
        return planes;
    }

    /** A chunk section's box, relative to a camera somewhere within render distance 16. */
    private static double[] randomBox(SplittableRandom random) {
        double x = random.nextInt(-16, 17) * 16 - random.nextDouble() * 16;
        double y = random.nextInt(0, 16) * 16 - random.nextDouble() * 256;
        double z = random.nextInt(-16, 17) * 16 - random.nextDouble() * 16;
        return new double[] {x, y, z, x + 16, y + 16, z + 16};
    }
}
