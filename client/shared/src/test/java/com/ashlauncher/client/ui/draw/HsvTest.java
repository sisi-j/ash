package com.ashlauncher.client.ui.draw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HsvTest {

    @Test
    void the_wheels_landmarks_are_where_they_should_be() {
        assertEquals(0xFF0000, Hsv.toRgb(0, 1, 1));
        assertEquals(0x00FF00, Hsv.toRgb(120, 1, 1));
        assertEquals(0x0000FF, Hsv.toRgb(240, 1, 1));
        assertEquals(0xFFFFFF, Hsv.toRgb(77, 0, 1));
        assertEquals(0x000000, Hsv.toRgb(77, 1, 0));
        assertEquals(0xFF0000, Hsv.toRgb(360, 1, 1), "a full turn is red again");
    }

    @Test
    void every_colour_survives_the_round_trip() {
        for (int rgb : new int[] {0xFF4D4D, 0x4DC3FF, 0x8B8C90, 0x123456, 0xFAFAFA, 0x0E0E0F, 0xFFE14D}) {
            float[] hsv = Hsv.fromRgb(rgb, null);
            assertEquals(rgb, Hsv.toRgb(hsv[0], hsv[1], hsv[2]), Integer.toHexString(rgb));
        }
    }

    @Test
    void grey_and_black_keep_the_hue_and_saturation_they_were_given() {
        float[] blue = {240, 1, 1};

        assertArrayEquals(new float[] {240, 0, 0.5f}, Hsv.fromRgb(0x808080, blue), 0.01f);
        assertArrayEquals(new float[] {240, 1, 0}, Hsv.fromRgb(0x000000, blue), 0.01f);
    }
}
