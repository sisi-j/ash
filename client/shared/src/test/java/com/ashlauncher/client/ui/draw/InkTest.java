package com.ashlauncher.client.ui.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pieces ash's interface is rasterised into, judged by their pixels. */
class InkTest {

    private static int alpha(Raster raster, int x, int y) {
        return raster.argb()[y * raster.width() + x] >>> 24;
    }

    @Test
    void a_corner_is_solid_inside_its_curve_clear_outside_and_soft_on_it() {
        Raster topLeft = Ink.corner(12, 0, 0xFFFFFFFF);

        assertEquals(12, topLeft.width());
        assertEquals(255, alpha(topLeft, 11, 11), "the corner's inner pixel");
        assertEquals(0, alpha(topLeft, 0, 0), "the corner's outer pixel");
        boolean soft = false;
        for (int i = 0; i < 12; i++) {
            int a = alpha(topLeft, i, i);
            soft |= a > 0 && a < 255;
        }
        assertTrue(soft, "the curve has no anti-aliased pixels");
    }

    @Test
    void each_quadrant_is_the_same_curve_turned() {
        Raster[] corners = new Raster[4];
        for (int q = 0; q < 4; q++) {
            corners[q] = Ink.corner(9, q, 0xFF000000);
        }
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int a = alpha(corners[0], x, y);
                assertEquals(a, alpha(corners[1], 8 - x, y), "top-right at " + x + "," + y);
                assertEquals(a, alpha(corners[2], 8 - x, 8 - y), "bottom-right at " + x + "," + y);
                assertEquals(a, alpha(corners[3], x, 8 - y), "bottom-left at " + x + "," + y);
            }
        }
    }

    @Test
    void a_corner_keeps_its_colour_s_own_alpha() {
        assertEquals(0x8C, alpha(Ink.corner(10, 0, 0x8C000000), 9, 9));
    }

    @Test
    void the_shadow_is_clear_against_the_rectangle_and_fades_out() {
        Raster top = Ink.shadow(10, 20, 0x59, 4);

        assertEquals(1, top.width());
        assertEquals(20, top.height());
        int nearest = top.argb()[19] >>> 24;
        int farthest = top.argb()[0] >>> 24;
        assertTrue(nearest > farthest, "the shadow does not fade outward: " + nearest + " to " + farthest);
        assertTrue(nearest <= 0x59);
        Raster corner = Ink.shadow(10, 20, 0x59, 0);
        assertEquals(0, alpha(corner, 29, 29), "the shadow reaches inside the rectangle");
    }

    @Test
    void text_carries_its_words_and_is_at_least_as_wide_as_it_advances() {
        Raster raster = Ink.text("Crosshair", Ink.Weight.SEMIBOLD, 18f, 0xFFFFFFFF);

        assertEquals("Crosshair", raster.text());
        assertTrue(raster.width() >= Ink.width("Crosshair", Ink.Weight.SEMIBOLD, 18f));
        boolean inked = false;
        for (int pixel : raster.argb()) {
            inked |= (pixel >>> 24) > 200;
        }
        assertTrue(inked, "nothing was drawn");
    }

    @Test
    void the_same_piece_asked_for_twice_is_the_same_raster() {
        assertSame(Ink.text("ENABLED", Ink.Weight.BOLD, 14f, 0xFFFFFFFF), Ink.text("ENABLED", Ink.Weight.BOLD, 14f, 0xFFFFFFFF));
        assertSame(Ink.corner(7, 2, 0xFF22C55E), Ink.corner(7, 2, 0xFF22C55E));
    }

    @Test
    void bolder_and_bigger_text_is_wider() {
        assertTrue(Ink.width("SETTINGS", Ink.Weight.EXTRABOLD, 20f) > Ink.width("SETTINGS", Ink.Weight.REGULAR, 20f));
        assertTrue(Ink.width("SETTINGS", Ink.Weight.REGULAR, 30f) > Ink.width("SETTINGS", Ink.Weight.REGULAR, 20f));
    }
}
