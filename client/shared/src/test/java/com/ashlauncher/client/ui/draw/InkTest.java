package com.ashlauncher.client.ui.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.ui.draw.Ink.Corner;
import com.ashlauncher.client.ui.draw.Ink.Edge;
import org.junit.jupiter.api.Test;

/** The pieces ash's interface is rasterised into, judged by their pixels. */
class InkTest {

    private static int alpha(Raster raster, int x, int y) {
        return raster.argb()[y * raster.width() + x] >>> 24;
    }

    @Test
    void a_corner_is_solid_inside_its_curve_clear_outside_and_soft_on_it() {
        Raster topLeft = Ink.corner(12, Corner.TOP_LEFT, 0xFFFFFFFF);

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
        for (Corner corner : Corner.values()) {
            corners[corner.ordinal()] = Ink.corner(9, corner, 0xFF000000);
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
        assertEquals(0x8C, alpha(Ink.corner(10, Corner.TOP_LEFT, 0x8C000000), 9, 9));
    }

    @Test
    void the_shadow_fades_out_from_its_edge_and_is_never_inside_the_rectangle() {
        Raster above = Ink.shadowEdge(10, 20, 0, 0x59, Edge.TOP);
        assertEquals(1, above.width());
        assertEquals(20, above.height());
        int nearest = above.argb()[19] >>> 24;
        int farthest = above.argb()[0] >>> 24;
        assertTrue(nearest > farthest, "the shadow does not fade outward: " + nearest + " to " + farthest);
        assertTrue(nearest <= 0x59);

        Raster topLeft = Ink.shadowCorner(10, 20, 0, 0x59, Corner.TOP_LEFT);
        assertEquals(0, alpha(topLeft, 29, 29), "the shadow reaches inside the rectangle");
    }

    @Test
    void a_dropped_shadow_is_full_strength_in_the_band_below_the_rectangle_with_no_gap() {
        Raster below = Ink.shadowEdge(10, 20, 8, 0x59, Edge.BOTTOM);

        assertEquals(28, below.height());
        for (int i = 0; i < 8; i++) {
            assertEquals(0x59, below.argb()[i] >>> 24, "the band under the rectangle at " + i);
        }
        assertTrue((below.argb()[27] >>> 24) < 0x10, "the shadow does not fade out below the band");

        Raster bottomLeft = Ink.shadowCorner(10, 20, 8, 0x59, Corner.BOTTOM_LEFT);
        assertEquals(38, bottomLeft.height(), "the corner does not reach down through the band");
        assertEquals(0, alpha(bottomLeft, 29, 0), "the shadow is inside the rectangle's corner");
        assertEquals(0x59, alpha(bottomLeft, 29, 12), "the band under the corner is not full strength");
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
        assertSame(Ink.corner(7, Corner.BOTTOM_RIGHT, 0xFF22C55E), Ink.corner(7, Corner.BOTTOM_RIGHT, 0xFF22C55E));
    }

    @Test
    void bolder_and_bigger_text_is_wider() {
        assertTrue(Ink.width("SETTINGS", Ink.Weight.EXTRABOLD, 20f) > Ink.width("SETTINGS", Ink.Weight.REGULAR, 20f));
        assertTrue(Ink.width("SETTINGS", Ink.Weight.REGULAR, 30f) > Ink.width("SETTINGS", Ink.Weight.REGULAR, 20f));
    }

    @Test
    void a_ring_corner_is_only_the_curve_s_last_pixel() {
        Raster ring = Ink.cornerRing(12, Corner.TOP_LEFT, 0xFFFFFFFF);

        assertEquals(0, alpha(ring, 11, 11), "the ring fills the corner's middle");
        assertEquals(0, alpha(ring, 0, 0), "the ring reaches outside the curve");
        int lit = 0;
        for (int pixel : ring.argb()) {
            lit += (pixel >>> 24) > 0 ? 1 : 0;
        }
        assertTrue(lit > 0 && lit < 12 * 12 / 3, lit + " pixels lit is not a thin ring");
    }

    @Test
    void wider_spacing_draws_wider_text() {
        assertTrue(Ink.width("ENABLED", Ink.Weight.BOLD, 14f, 0.06f) > Ink.width("ENABLED", Ink.Weight.BOLD, 14f));
    }
}
