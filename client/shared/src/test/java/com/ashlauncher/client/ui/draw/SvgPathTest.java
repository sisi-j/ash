package com.ashlauncher.client.ui.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The path reader against path data from Lucide's own icons, judged by where
 * the outline really goes: every shape is flattened to short lines and its
 * points compared, so a curve's control points never stand in for the curve.
 */
class SvgPathTest {

    private static final double CLOSE = 0.02;

    /** The points the outline passes through, flattened finely. */
    private static List<double[]> points(Shape shape) {
        List<double[]> points = new ArrayList<>();
        double[] coords = new double[6];
        for (PathIterator it = shape.getPathIterator(null, 0.001); !it.isDone(); it.next()) {
            if (it.currentSegment(coords) != PathIterator.SEG_CLOSE) {
                points.add(new double[] {coords[0], coords[1]});
            }
        }
        return points;
    }

    private static Rectangle2D bounds(Shape shape) {
        Rectangle2D.Double bounds = null;
        for (double[] p : points(shape)) {
            if (bounds == null) {
                bounds = new Rectangle2D.Double(p[0], p[1], 0, 0);
            } else {
                bounds.add(p[0], p[1]);
            }
        }
        return bounds;
    }

    private static void assertPoint(double x, double y, double[] point) {
        assertEquals(x, point[0], CLOSE, "x");
        assertEquals(y, point[1], CLOSE, "y");
    }

    @Test
    void gauges_arc_is_the_top_of_a_circle_of_ten_round_twelve_fourteen() {
        // Lucide's gauge: from (3.34, 19) to (20.66, 19), the long way round
        // a radius-10 circle. Its centre is 5 above the chord, at (12, 14).
        Rectangle2D arc = bounds(SvgPath.parse("M3.34 19a10 10 0 1 1 17.32 0"));

        assertEquals(2, arc.getMinX(), CLOSE);
        assertEquals(22, arc.getMaxX(), CLOSE);
        assertEquals(4, arc.getMinY(), CLOSE);
        assertEquals(19, arc.getMaxY(), CLOSE);
    }

    @Test
    void every_point_of_an_arc_is_on_its_circle() {
        for (double[] p : points(SvgPath.parse("M3.34 19a10 10 0 1 1 17.32 0"))) {
            assertEquals(10, Math.hypot(p[0] - 12, p[1] - 14), 0.01, "off the circle at " + p[0] + "," + p[1]);
        }
    }

    @Test
    void the_sweep_flag_picks_the_side_the_arc_goes_round() {
        // From (0,0) to (10,0) on a radius-5 circle: sweep 1 is clockwise on
        // screen, over the top, where y is negative; sweep 0 goes under.
        assertEquals(-5, bounds(SvgPath.parse("M0 0A5 5 0 0 1 10 0")).getMinY(), CLOSE);
        assertEquals(5, bounds(SvgPath.parse("M0 0A5 5 0 0 0 10 0")).getMaxY(), CLOSE);
    }

    @Test
    void arc_flags_packed_against_the_next_number_are_read_as_lucide_writes_them() {
        // From Lucide's swords: "a2 2 0 013 5.172" is flags 0 and 1, then 3.
        List<double[]> points = points(SvgPath.parse("M5.172 3A2 2 0 013 5.172"));

        assertPoint(3, 5.172, points.get(points.size() - 1));
    }

    @Test
    void a_moves_further_pairs_are_lines_and_relative_commands_are_from_the_last_point() {
        // Lucide's chevrons-right, first stroke: "m6 17 5-5-5-5".
        List<double[]> points = points(SvgPath.parse("m6 17 5-5-5-5"));

        assertEquals(3, points.size());
        assertPoint(6, 17, points.get(0));
        assertPoint(11, 12, points.get(1));
        assertPoint(6, 7, points.get(2));
    }

    @Test
    void numbers_in_every_form_path_data_allows() {
        // "-.5.5" is two numbers, and an exponent is a number's own.
        List<double[]> points = points(SvgPath.parse("M-.5.5L1e1-2h+3V4.25e-1"));

        assertPoint(-0.5, 0.5, points.get(0));
        assertPoint(10, -2, points.get(1));
        assertPoint(13, -2, points.get(2));
        assertPoint(13, 0.425, points.get(3));
    }

    @Test
    void a_smooth_curve_reflects_the_last_control_point() {
        // Two halves of an S: the second's first control point is the
        // first's second, reflected through (10, 0).
        Rectangle2D s = bounds(SvgPath.parse("M0 0C0 -10 10 -10 10 0S20 10 20 0"));

        assertTrue(s.getMinY() < -7 && s.getMaxY() > 7, "the second half did not mirror the first: " + s);
    }

    @Test
    void close_goes_back_to_the_start_of_the_path() {
        List<double[]> points = points(SvgPath.parse("M2 2h4v4zl1 1"));

        assertPoint(3, 3, points.get(points.size() - 1));
    }

    @Test
    void malformed_data_is_an_error_not_part_of_a_path() {
        assertThrows(IllegalArgumentException.class, () -> SvgPath.parse("1 2"));
        assertThrows(IllegalArgumentException.class, () -> SvgPath.parse("M1"));
        assertThrows(IllegalArgumentException.class, () -> SvgPath.parse("M0 0A5 5 0 2 1 10 0"));
        assertThrows(IllegalArgumentException.class, () -> SvgPath.parse("M0 0X1 1"));
    }

    @Test
    void every_icon_ash_ships_reads_and_stays_on_lucides_grid() {
        for (Ink.Icon icon : Ink.Icon.values()) {
            List<Shape> shapes = LucideIcon.shapes(icon.lucideName());
            assertFalse(shapes.isEmpty(), icon + " drew nothing");
            for (Shape shape : shapes) {
                Rectangle2D b = bounds(shape);
                assertTrue(b.getMinX() >= 0 && b.getMinY() >= 0 && b.getMaxX() <= 24 && b.getMaxY() <= 24,
                        icon + " leaves the 24-unit grid: " + b);
            }
        }
    }

    @Test
    void lucides_shapes_are_read_as_their_elements_say() {
        // Lucide's crosshair: a circle of 10 and four lines.
        List<Shape> crosshair = LucideIcon.shapes("crosshair");
        assertEquals(5, crosshair.size());
        Rectangle2D circle = bounds(crosshair.get(0));
        assertEquals(2, circle.getMinX(), CLOSE);
        assertEquals(22, circle.getMaxY(), CLOSE);

        // And layout-dashboard's four rounded rects, the first 7 by 9 at (3, 3).
        Rectangle2D first = bounds(LucideIcon.shapes("layout-dashboard").get(0));
        assertEquals(3, first.getMinX(), CLOSE);
        assertEquals(12, first.getMaxY(), CLOSE);
    }

    @Test
    void an_element_ash_does_not_draw_is_refused() {
        assertThrows(IllegalArgumentException.class,
                () -> LucideIcon.parse("<svg viewBox=\"0 0 24 24\"><text x=\"1\">no</text></svg>"));
    }

    @Test
    void an_icon_is_drawn_on_its_strokes_and_nowhere_else() {
        // The crosshair at 24 pixels: its circle's stroke covers (12, 2), its
        // middle is empty.
        Raster crosshair = Ink.icon(Ink.Icon.CROSSHAIR, 24, 0xFFFFFFFF);
        int[] argb = crosshair.argb();

        assertTrue((argb[2 * 24 + 12] >>> 24) > 0xC0, "nothing on the circle's stroke");
        assertEquals(0, argb[12 * 24 + 12] >>> 24, "something in the middle");
    }

    @Test
    void lucides_notices_ship_beside_its_icons() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/assets/ash/icons/LICENSE.txt")) {
            String notice = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(notice.contains("ISC License"), "no ISC notice");
            assertTrue(notice.contains("Cole Bemis"), "no MIT notice for the Feather-derived icons");
        }
    }
}
