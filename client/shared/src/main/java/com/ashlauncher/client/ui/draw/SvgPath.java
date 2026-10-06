package com.ashlauncher.client.ui.draw;

import java.awt.geom.Path2D;

/**
 * SVG path data as a Java 2D path: what an icon is drawn from
 * ({@code docs/research/0007}, section 4).
 *
 * <p>All of path data: move, line, horizontal, vertical, cubic and quadratic
 * curves with their smooth forms, arcs, and close, each absolute or
 * relative, with a command's arguments repeated for as many segments as they
 * fill. Numbers in every form path data allows them - {@code -.5.5} is two
 * numbers - and arc flags written without a separator, as Lucide writes them:
 * {@code a2 2 0 013 5.172}.
 *
 * <p>An arc becomes cubic curves of at most a quarter turn each, from its
 * centre worked out as the SVG specification does (appendix F.6.5), so it
 * rasterises as smoothly as any curve.
 */
public final class SvgPath {

    private final String data;
    private int at;
    /** The kind of the segment before this one: a smooth curve reflects only a curve of its own kind. */
    private char lastKind;

    private SvgPath(String data) {
        this.data = data;
    }

    /** The path {@code d} draws; a malformed one is an error, not a partial path. */
    public static Path2D.Double parse(String d) {
        return new SvgPath(d).read();
    }

    private Path2D.Double read() {
        Path2D.Double path = new Path2D.Double();
        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        // The last control point, for a smooth curve's reflection.
        double controlX = 0;
        double controlY = 0;
        char command = 0;

        skipSeparators();
        while (at < data.length()) {
            char c = data.charAt(at);
            if (isCommand(c)) {
                command = c;
                at++;
            } else if (command == 0) {
                throw malformed("a number before any command");
            } else if (command == 'M') {
                // Pairs after a move's first are lines.
                command = 'L';
            } else if (command == 'm') {
                command = 'l';
            } else if (command == 'Z' || command == 'z') {
                throw malformed("a number after a close");
            }

            boolean relative = Character.isLowerCase(command);
            double baseX = relative ? x : 0;
            double baseY = relative ? y : 0;
            char kind = Character.toUpperCase(command);
            switch (kind) {
                case 'M':
                    x = baseX + number();
                    y = baseY + number();
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    controlX = x;
                    controlY = y;
                    break;
                case 'L':
                    x = baseX + number();
                    y = baseY + number();
                    path.lineTo(x, y);
                    controlX = x;
                    controlY = y;
                    break;
                case 'H':
                    x = baseX + number();
                    path.lineTo(x, y);
                    controlX = x;
                    controlY = y;
                    break;
                case 'V':
                    y = baseY + number();
                    path.lineTo(x, y);
                    controlX = x;
                    controlY = y;
                    break;
                case 'C': {
                    double x1 = baseX + number();
                    double y1 = baseY + number();
                    double x2 = baseX + number();
                    double y2 = baseY + number();
                    x = baseX + number();
                    y = baseY + number();
                    path.curveTo(x1, y1, x2, y2, x, y);
                    controlX = x2;
                    controlY = y2;
                    break;
                }
                case 'S': {
                    boolean follows = isAny(lastKind, 'C', 'S');
                    double x1 = follows ? 2 * x - controlX : x;
                    double y1 = follows ? 2 * y - controlY : y;
                    double x2 = baseX + number();
                    double y2 = baseY + number();
                    x = baseX + number();
                    y = baseY + number();
                    path.curveTo(x1, y1, x2, y2, x, y);
                    controlX = x2;
                    controlY = y2;
                    break;
                }
                case 'Q': {
                    double x1 = baseX + number();
                    double y1 = baseY + number();
                    x = baseX + number();
                    y = baseY + number();
                    path.quadTo(x1, y1, x, y);
                    controlX = x1;
                    controlY = y1;
                    break;
                }
                case 'T': {
                    boolean follows = isAny(lastKind, 'Q', 'T');
                    double x1 = follows ? 2 * x - controlX : x;
                    double y1 = follows ? 2 * y - controlY : y;
                    x = baseX + number();
                    y = baseY + number();
                    path.quadTo(x1, y1, x, y);
                    controlX = x1;
                    controlY = y1;
                    break;
                }
                case 'A': {
                    double rx = number();
                    double ry = number();
                    double rotation = number();
                    boolean large = flag();
                    boolean sweep = flag();
                    double toX = baseX + number();
                    double toY = baseY + number();
                    arc(path, x, y, rx, ry, rotation, large, sweep, toX, toY);
                    x = toX;
                    y = toY;
                    controlX = x;
                    controlY = y;
                    break;
                }
                case 'Z':
                    path.closePath();
                    x = startX;
                    y = startY;
                    controlX = x;
                    controlY = y;
                    break;
                default:
                    throw malformed("an unknown command " + command);
            }
            lastKind = kind;
            skipSeparators();
        }
        return path;
    }

    private static boolean isAny(char c, char a, char b) {
        return c == a || c == b;
    }

    private static boolean isCommand(char c) {
        return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
    }

    private void skipSeparators() {
        while (at < data.length()) {
            char c = data.charAt(at);
            if (c == ',' || Character.isWhitespace(c)) {
                at++;
            } else {
                return;
            }
        }
    }

    /** One number: a sign, digits with at most one point, and an exponent. */
    private double number() {
        skipSeparators();
        int start = at;
        if (at < data.length() && (data.charAt(at) == '-' || data.charAt(at) == '+')) {
            at++;
        }
        boolean digits = false;
        boolean point = false;
        while (at < data.length()) {
            char c = data.charAt(at);
            if (Character.isDigit(c)) {
                digits = true;
                at++;
            } else if (c == '.' && !point) {
                point = true;
                at++;
            } else {
                break;
            }
        }
        if (digits && at < data.length() && (data.charAt(at) == 'e' || data.charAt(at) == 'E')) {
            int mark = at;
            at++;
            if (at < data.length() && (data.charAt(at) == '-' || data.charAt(at) == '+')) {
                at++;
            }
            int exponentStart = at;
            while (at < data.length() && Character.isDigit(data.charAt(at))) {
                at++;
            }
            if (at == exponentStart) {
                at = mark;
            }
        }
        if (!digits) {
            throw malformed("a number expected at " + start);
        }
        return Double.parseDouble(data.substring(start, at));
    }

    /** An arc flag: one character, 0 or 1, and nothing need separate it from what follows. */
    private boolean flag() {
        skipSeparators();
        if (at < data.length() && (data.charAt(at) == '0' || data.charAt(at) == '1')) {
            return data.charAt(at++) == '1';
        }
        throw malformed("an arc flag expected at " + at);
    }

    private IllegalArgumentException malformed(String what) {
        return new IllegalArgumentException("malformed path data, " + what + ": " + data);
    }

    /**
     * An elliptical arc from ({@code x0}, {@code y0}) to ({@code x}, {@code y}),
     * as cubic curves: the centre first, by the SVG specification's
     * appendix F.6.5, then each quarter turn or less as a curve.
     */
    static void arc(Path2D.Double path, double x0, double y0, double rx, double ry, double rotation,
            boolean large, boolean sweep, double x, double y) {
        if (x0 == x && y0 == y) {
            return;
        }
        if (rx == 0 || ry == 0) {
            path.lineTo(x, y);
            return;
        }
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        double phi = Math.toRadians(rotation);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double halfX = (x0 - x) / 2;
        double halfY = (y0 - y) / 2;
        double x1 = cos * halfX + sin * halfY;
        double y1 = -sin * halfX + cos * halfY;

        // Radii too small to reach are scaled up until they just do.
        double reach = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
        if (reach > 1) {
            rx *= Math.sqrt(reach);
            ry *= Math.sqrt(reach);
        }
        double numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double scale = Math.sqrt(Math.max(0, numerator / denominator));
        if (large == sweep) {
            scale = -scale;
        }
        double cx1 = scale * rx * y1 / ry;
        double cy1 = -scale * ry * x1 / rx;
        double cx = cos * cx1 - sin * cy1 + (x0 + x) / 2;
        double cy = sin * cx1 + cos * cy1 + (y0 + y) / 2;

        double start = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
        double extent = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry);
        if (!sweep && extent > 0) {
            extent -= 2 * Math.PI;
        } else if (sweep && extent < 0) {
            extent += 2 * Math.PI;
        }

        int segments = Math.max(1, (int) Math.ceil(Math.abs(extent) / (Math.PI / 2) - 1e-9));
        double step = extent / segments;
        double handle = 4.0 / 3.0 * Math.tan(step / 4);
        double from = start;
        for (int i = 0; i < segments; i++) {
            double to = from + step;
            double ax = Math.cos(from);
            double ay = Math.sin(from);
            double bx = Math.cos(to);
            double by = Math.sin(to);
            double[] c1 = onEllipse(ax - handle * ay, ay + handle * ax, cx, cy, rx, ry, cos, sin);
            double[] c2 = onEllipse(bx + handle * by, by - handle * bx, cx, cy, rx, ry, cos, sin);
            double[] end = i == segments - 1 ? new double[] {x, y} : onEllipse(bx, by, cx, cy, rx, ry, cos, sin);
            path.curveTo(c1[0], c1[1], c2[0], c2[1], end[0], end[1]);
            from = to;
        }
    }

    /** A point on the unit circle, carried onto the arc's ellipse. */
    private static double[] onEllipse(double ux, double uy, double cx, double cy, double rx, double ry,
            double cos, double sin) {
        return new double[] {cx + rx * ux * cos - ry * uy * sin, cy + rx * ux * sin + ry * uy * cos};
    }

    /** The signed angle from one vector to another. */
    private static double angle(double ux, double uy, double vx, double vy) {
        return Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
    }
}
