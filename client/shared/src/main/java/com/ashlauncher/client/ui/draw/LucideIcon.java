package com.ashlauncher.client.ui.draw;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One of Lucide's icons, read from its own SVG file as Lucide publishes it:
 * the files under {@code assets/ash/icons} are Lucide 1.52.0's, unchanged,
 * with its licence beside them.
 *
 * <p>Every Lucide icon is outlines on a 24-unit grid, stroked 2 wide with
 * round caps and joins. So an icon is its shapes, and what they are drawn
 * with is the same for all of them - {@link Ink#icon} does that. This reads
 * the shape elements Lucide uses - path, circle, ellipse, line, rect,
 * polyline and polygon - and refuses anything else, so an icon that would
 * draw wrongly fails a test instead.
 */
final class LucideIcon {

    private static final Pattern ELEMENT = Pattern.compile("<(\\w+)\\b([^>]*?)/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*\"([^\"]*)\"");

    private LucideIcon() {
    }

    /** The shapes of the icon in {@code assets/ash/icons/<name>.svg}, on its 24-unit grid. */
    static List<Shape> shapes(String name) {
        String resource = "/assets/ash/icons/" + name + ".svg";
        try (InputStream in = LucideIcon.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no icon " + resource + " in the client");
            }
            return parse(read(in));
        } catch (IOException unreadable) {
            throw new IllegalStateException("icon " + resource + " could not be read", unreadable);
        }
    }

    /** The shapes an SVG document draws, outside its {@code svg} element itself. */
    static List<Shape> parse(String svg) {
        List<Shape> shapes = new ArrayList<>();
        Matcher element = ELEMENT.matcher(svg);
        while (element.find()) {
            String tag = element.group(1);
            if (tag.equals("svg")) {
                continue;
            }
            Map<String, String> a = attributes(element.group(2));
            switch (tag) {
                case "path":
                    shapes.add(SvgPath.parse(required(a, "d", tag)));
                    break;
                case "circle": {
                    double r = number(a, "r");
                    shapes.add(new Ellipse2D.Double(number(a, "cx") - r, number(a, "cy") - r, 2 * r, 2 * r));
                    break;
                }
                case "ellipse": {
                    double rx = number(a, "rx");
                    double ry = number(a, "ry");
                    shapes.add(new Ellipse2D.Double(number(a, "cx") - rx, number(a, "cy") - ry, 2 * rx, 2 * ry));
                    break;
                }
                case "line":
                    shapes.add(new Line2D.Double(number(a, "x1"), number(a, "y1"), number(a, "x2"), number(a, "y2")));
                    break;
                case "rect":
                    shapes.add(rect(a));
                    break;
                case "polyline":
                case "polygon":
                    shapes.add(points(required(a, "points", tag), tag.equals("polygon")));
                    break;
                default:
                    throw new IllegalArgumentException("an icon element ash does not draw: <" + tag + ">");
            }
        }
        return Collections.unmodifiableList(shapes);
    }

    /** A rect, rounded by {@code rx} and {@code ry} - each standing in for the other when only one is given. */
    private static Shape rect(Map<String, String> a) {
        double x = number(a, "x");
        double y = number(a, "y");
        double width = number(a, "width");
        double height = number(a, "height");
        String rxRaw = a.get("rx");
        String ryRaw = a.get("ry");
        double rx = rxRaw != null ? Double.parseDouble(rxRaw) : ryRaw != null ? Double.parseDouble(ryRaw) : 0;
        double ry = ryRaw != null ? Double.parseDouble(ryRaw) : rx;
        if (rx == 0 && ry == 0) {
            return new Rectangle2D.Double(x, y, width, height);
        }
        return new RoundRectangle2D.Double(x, y, width, height, 2 * rx, 2 * ry);
    }

    private static Shape points(String raw, boolean closed) {
        String[] values = raw.trim().split("[\\s,]+");
        if (values.length < 4 || values.length % 2 != 0) {
            throw new IllegalArgumentException("malformed points: " + raw);
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
        for (int i = 2; i < values.length; i += 2) {
            path.lineTo(Double.parseDouble(values[i]), Double.parseDouble(values[i + 1]));
        }
        if (closed) {
            path.closePath();
        }
        return path;
    }

    private static Map<String, String> attributes(String raw) {
        Map<String, String> attributes = new HashMap<>();
        Matcher attribute = ATTRIBUTE.matcher(raw);
        while (attribute.find()) {
            attributes.put(attribute.group(1), attribute.group(2));
        }
        return attributes;
    }

    /** A number attribute, 0 when absent, as SVG has it for these shapes' positions. */
    private static double number(Map<String, String> a, String name) {
        String raw = a.get(name);
        return raw == null ? 0 : Double.parseDouble(raw);
    }

    private static String required(Map<String, String> a, String name, String tag) {
        String raw = a.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("<" + tag + "> without " + name);
        }
        return raw;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
