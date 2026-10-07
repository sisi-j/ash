package com.ashlauncher.client.ui.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Rasterises the pieces of ash's interface with Java 2D, in Inter, and keeps
 * them: a piece is drawn once and reused until it is pushed out of the cache.
 *
 * <p>Java 2D rather than either game's own text or shapes, because it is in
 * both games' runtimes and draws the same pixels in both
 * (`docs/research/0007`): 1.21.11's own TrueType text samples its glyphs with
 * nearest-neighbour filtering at one fixed oversampling, and 1.8.9 has no
 * TrueType text at all. Everything here is plain Java 8 and knows nothing of
 * either game.
 *
 * <p>Called on the game's render thread. Fonts load once, on a thread of
 * their own, as soon as {@link #preload} is called at startup, so the first
 * panel does not wait for them. If Inter cannot be read, the system's sans
 * serif stands in, so the panel still works, and the target is told why.
 */
public final class Ink {

    /** The weights of Inter ash ships: the static TrueType files, since Java 2D cannot drive a variable font's axes. */
    public enum Weight {
        REGULAR("Inter-Regular.ttf", Font.PLAIN),
        SEMIBOLD("Inter-SemiBold.ttf", Font.BOLD),
        BOLD("Inter-Bold.ttf", Font.BOLD),
        EXTRABOLD("Inter-ExtraBold.ttf", Font.BOLD);

        private final String file;
        /** The system font's style that stands in for it if Inter cannot be read. */
        private final int fallbackStyle;

        Weight(String file, int fallbackStyle) {
            this.file = file;
            this.fallbackStyle = fallbackStyle;
        }
    }

    /** A corner of a rectangle, and the quarter of a disc that rounds it. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT;

        boolean left() {
            return this == TOP_LEFT || this == BOTTOM_LEFT;
        }

        boolean top() {
            return this == TOP_LEFT || this == TOP_RIGHT;
        }
    }

    /** A straight edge of a rectangle. */
    public enum Edge {
        TOP, RIGHT, BOTTOM, LEFT;

        boolean horizontal() {
            return this == TOP || this == BOTTOM;
        }

        /** Whether the piece's first pixel is the one farthest from the rectangle. */
        boolean startsOutside() {
            return this == TOP || this == LEFT;
        }
    }

    /** The icons ash draws: each one of Lucide's, by its Lucide name. */
    public enum Icon {
        BACK("arrow-left"),
        GEAR("settings"),
        LAYOUT("layout-dashboard"),
        SEARCH("search"),
        CROSSHAIR("crosshair"),
        HIT("zap"),
        DROPLET("droplet"),
        GAUGE("gauge"),
        SIGNAL("signal"),
        SPRINT("chevrons-right"),
        EYE("eye"),
        ROTATE("rotate-ccw");

        private final String lucideName;

        Icon(String lucideName) {
            this.lucideName = lucideName;
        }

        /** Its file under {@code assets/ash/icons}, without the {@code .svg}. */
        public String lucideName() {
            return lucideName;
        }
    }

    /** Each icon's shapes, read once. */
    private static final Map<Icon, List<Shape>> ICON_SHAPES = new EnumMap<>(Icon.class);

    /** At most this many pixels are kept across every cached raster: 32 MB of ARGB. */
    private static final long MAX_CACHED_PIXELS = 8L * 1024 * 1024;

    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);

    private static FutureTask<Map<Weight, Font>> loading;
    private static Consumer<String> warn = message -> { };
    private static final Map<String, Font> SIZED = new HashMap<>();
    private static long cachedPixels;
    private static final LinkedHashMap<String, Raster> CACHE = new LinkedHashMap<>(256, 0.75f, true);

    private Ink() {
    }

    /**
     * Starts loading Inter on a thread of its own. Call once, at startup;
     * calling again does nothing.
     *
     * @param warnings told, once, if Inter could not be read and the system's sans serif stands in
     */
    public static synchronized void preload(Consumer<String> warnings) {
        if (loading == null) {
            warn = warnings;
            loading = new FutureTask<>(Ink::loadFonts);
            Thread thread = new Thread(loading, "ash fonts");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static Map<Weight, Font> loadFonts() {
        Map<Weight, Font> fonts = new EnumMap<>(Weight.class);
        for (Weight weight : Weight.values()) {
            try (InputStream in = Ink.class.getResourceAsStream("/assets/ash/fonts/" + weight.file)) {
                if (in == null) {
                    throw new java.io.IOException("it is missing from the client");
                }
                fonts.put(weight, Font.createFont(Font.TRUETYPE_FONT, in));
            } catch (java.io.IOException | java.awt.FontFormatException | RuntimeException unreadable) {
                warn.accept("ash: " + weight.file + " could not be read (" + unreadable
                        + "), so ash's settings use the system's sans serif in its place");
                fonts.put(weight, new Font(Font.SANS_SERIF, weight.fallbackStyle, 1));
            }
        }
        return fonts;
    }

    private static synchronized Font font(Weight weight, float size, float tracking) {
        String key = weight + "/" + size + "/" + tracking;
        Font sized = SIZED.get(key);
        if (sized == null) {
            preload(warn);
            Font base;
            try {
                base = loading.get().get(weight);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                base = new Font(Font.SANS_SERIF, weight.fallbackStyle, 1);
            } catch (ExecutionException failed) {
                base = new Font(Font.SANS_SERIF, weight.fallbackStyle, 1);
            }
            sized = base.deriveFont(size);
            if (tracking != 0) {
                Map<TextAttribute, Object> spacing = new HashMap<>();
                spacing.put(TextAttribute.TRACKING, tracking);
                sized = sized.deriveFont(spacing);
            }
            SIZED.put(key, sized);
        }
        return sized;
    }

    // ---- text ----

    /** How far {@code text} advances, in real pixels, rounded up. */
    public static int width(String text, Weight weight, float size) {
        return width(text, weight, size, 0f);
    }

    /** @param tracking extra space after each letter, as a fraction of the size: the mockup's letter-spacing in em */
    public static int width(String text, Weight weight, float size, float tracking) {
        return (int) Math.ceil(font(weight, size, tracking).getStringBounds(text, METRICS).getWidth());
    }

    /** The height of one line, ascent and descent, in real pixels, rounded up. */
    public static int lineHeight(Weight weight, float size) {
        LineMetrics line = font(weight, size, 0f).getLineMetrics("Hg", METRICS);
        return (int) Math.ceil(line.getAscent() + line.getDescent());
    }

    /** The height of a capital letter, for placing a line of capitals by eye rather than by its descent. */
    public static int capHeight(Weight weight, float size) {
        return (int) Math.round(font(weight, size, 0f).createGlyphVector(METRICS, "H").getVisualBounds().getHeight());
    }

    /**
     * One line of text, its origin at the top-left of its line box: draw it
     * with {@link Raster#drawAt}. It has a margin all round, so that a glyph
     * reaching outside its advance is not cut off.
     */
    public static Raster text(String text, Weight weight, float size, int argb) {
        return text(text, weight, size, argb, 0f);
    }

    public static synchronized Raster text(String text, Weight weight, float size, int argb, float tracking) {
        return cached("text/" + weight + "/" + size + "/" + tracking + "/" + Integer.toHexString(argb) + "/" + text, () -> {
            Font font = font(weight, size, tracking);
            LineMetrics line = font.getLineMetrics(text.isEmpty() ? "H" : text, METRICS);
            int margin = (int) Math.ceil(size * 0.25f) + 1;
            int w = width(text, weight, size, tracking) + 2 * margin;
            int h = (int) Math.ceil(line.getAscent() + line.getDescent()) + 2 * margin;
            BufferedImage image = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(image);
            g.setFont(font);
            g.setColor(new Color(argb, true));
            g.drawString(text, (float) margin, margin + line.getAscent());
            g.dispose();
            return raster(image, margin, margin, text);
        });
    }

    // ---- shapes ----

    /**
     * One corner of a rounded rectangle, anti-aliased: a quarter disc of
     * radius {@code radius}. Computed by sampling, not by Java 2D, so that
     * the edge is the same on every runtime.
     */
    public static synchronized Raster corner(int radius, Corner corner, int argb) {
        return cached("corner/" + radius + "/" + corner + "/" + Integer.toHexString(argb),
                () -> quarter(radius, corner, argb, 0));
    }

    /** One corner of a rounded rectangle's one-pixel outline: the quarter ring between {@code radius - 1} and {@code radius}. */
    public static synchronized Raster cornerRing(int radius, Corner corner, int argb) {
        return cached("ring/" + radius + "/" + corner + "/" + Integer.toHexString(argb),
                () -> quarter(radius, corner, argb, radius - 1));
    }

    /** A quarter of an annulus from {@code inner} to {@code radius}, sampled four by four per pixel. */
    private static Raster quarter(int radius, Corner corner, int argb, int inner) {
        int[] pixels = new int[radius * radius];
        int alpha = argb >>> 24;
        int samples = 4;
        for (int y = 0; y < radius; y++) {
            for (int x = 0; x < radius; x++) {
                int inside = 0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        double dx = toCentre(corner.left(), radius, x + (sx + 0.5) / samples);
                        double dy = toCentre(corner.top(), radius, y + (sy + 0.5) / samples);
                        double d2 = dx * dx + dy * dy;
                        if (d2 <= (double) radius * radius && d2 >= (double) inner * inner) {
                            inside++;
                        }
                    }
                }
                int a = Math.round(alpha * inside / (float) (samples * samples));
                pixels[y * radius + x] = (a << 24) | (argb & 0xFFFFFF);
            }
        }
        return new Raster(radius, radius, pixels, 0, 0, null);
    }

    /**
     * How far a point is from a corner piece's centre along one axis: the
     * centre sits on the piece's inner corner, at {@code size} from the
     * outer edge on a left or top piece and at 0 on a right or bottom one.
     */
    private static double toCentre(boolean leftOrTop, int size, double at) {
        return leftOrTop ? size - at : at;
    }

    /*
     * The soft shadow of a rounded rectangle lowered by a drop, as light from
     * above casts it, drawn only where the rectangle itself is not: none
     * inside it, full strength where its lowered copy shows below it, and
     * fading out to {@code reach} past the copy's edge. Each piece is worked
     * out per pixel against both shapes, in a frame of a rectangle large
     * enough that its corners never see each other, so a piece depends on
     * the radius, reach, drop and strength alone.
     */

    /** The side of the large rectangle each shadow piece is worked out against. */
    private static final int FRAME = 4096;

    /**
     * A corner of the shadow: four pieces, each as wide as the radius plus
     * the reach; the bottom two also as tall as the drop more, so that they
     * fill the band the lowered copy shows below the rectangle's corners.
     * Placed by {@link Paint#shadow}.
     */
    public static synchronized Raster shadowCorner(int radius, int reach, int drop, int alpha, Corner corner) {
        return cached("shadow/" + radius + "/" + reach + "/" + drop + "/" + alpha + "/" + corner, () -> {
            int w = radius + reach;
            int h = corner.top() ? radius + reach : radius + drop + reach;
            int x0 = corner.left() ? -reach : FRAME - radius;
            int y0 = corner.top() ? drop - reach : FRAME - radius;
            return shadowPiece(radius, reach, drop, alpha, x0, y0, w, h);
        });
    }

    /**
     * A one-pixel strip of the shadow along an edge, to be stretched along it:
     * above the top, the part of the reach the drop leaves showing; below the
     * bottom, the drop's band at full strength and then the reach.
     */
    public static synchronized Raster shadowEdge(int radius, int reach, int drop, int alpha, Edge edge) {
        return cached("shadow-edge/" + radius + "/" + reach + "/" + drop + "/" + alpha + "/" + edge, () -> {
            switch (edge) {
                case TOP:
                    return shadowPiece(radius, reach, drop, alpha, FRAME / 2, drop - reach, 1, Math.max(1, reach - drop));
                case BOTTOM:
                    return shadowPiece(radius, reach, drop, alpha, FRAME / 2, FRAME, 1, drop + reach);
                case LEFT:
                    return shadowPiece(radius, reach, drop, alpha, -reach, FRAME / 2, reach, 1);
                default:
                    return shadowPiece(radius, reach, drop, alpha, FRAME, FRAME / 2, reach, 1);
            }
        });
    }

    private static Raster shadowPiece(int radius, int reach, int drop, int alpha, int x0, int y0, int w, int h) {
        int[] pixels = new int[w * h];
        double half = FRAME / 2.0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double px = x0 + x + 0.5;
                double py = y0 + y + 0.5;
                if (roundedDistance(px, py, half, half, half, radius) < 0) {
                    continue;
                }
                double out = roundedDistance(px, py, half, half + drop, half, radius);
                pixels[y * w + x] = out <= 0 ? alpha << 24 : shadowAt(out, reach, alpha);
            }
        }
        return new Raster(w, h, pixels, 0, 0, null);
    }

    /** How far a point is outside a rounded square of half-side {@code half} centred at (cx, cy): negative inside. */
    private static double roundedDistance(double px, double py, double cx, double cy, double half, int radius) {
        double qx = Math.abs(px - cx) - (half - radius);
        double qy = Math.abs(py - cy) - (half - radius);
        double outside = Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - radius;
    }

    private static int shadowAt(double out, int reach, int alpha) {
        if (out <= 0 || out >= reach) {
            return 0;
        }
        double fade = 1 - out / reach;
        return ((int) Math.round(alpha * fade * fade)) << 24;
    }

    // ---- icons ----

    /**
     * One of Lucide's icons, square, {@code size} pixels across: its 24-unit
     * outlines scaled to fit and stroked as Lucide draws them - 2 units wide,
     * round caps and joins - at this exact size, so it is crisp at any.
     */
    public static synchronized Raster icon(Icon icon, int size, int argb) {
        return cached("icon/" + icon + "/" + size + "/" + Integer.toHexString(argb), () -> {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(image);
            g.setColor(new Color(argb, true));
            g.scale(size / 24.0, size / 24.0);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Shape shape : shapesOf(icon)) {
                g.draw(shape);
            }
            g.dispose();
            return raster(image, 0, 0, null);
        });
    }

    private static List<Shape> shapesOf(Icon icon) {
        List<Shape> shapes = ICON_SHAPES.get(icon);
        if (shapes == null) {
            shapes = LucideIcon.shapes(icon.lucideName());
            ICON_SHAPES.put(icon, shapes);
        }
        return shapes;
    }

    // ---- the cache ----

    private static Raster cached(String key, Supplier<Raster> make) {
        Raster raster = CACHE.get(key);
        if (raster != null) {
            return raster;
        }
        raster = make.get();
        CACHE.put(key, raster);
        cachedPixels += (long) raster.width() * raster.height();
        Iterator<Map.Entry<String, Raster>> oldest = CACHE.entrySet().iterator();
        while (cachedPixels > MAX_CACHED_PIXELS && oldest.hasNext()) {
            Map.Entry<String, Raster> entry = oldest.next();
            if (entry.getValue() == raster) {
                break;
            }
            cachedPixels -= (long) entry.getValue().width() * entry.getValue().height();
            oldest.remove();
        }
        return raster;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static Raster raster(BufferedImage image, int originX, int originY, String text) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        return new Raster(image.getWidth(), image.getHeight(), pixels, originX, originY, text);
    }
}
