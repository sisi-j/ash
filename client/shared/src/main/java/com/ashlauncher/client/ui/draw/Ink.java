package com.ashlauncher.client.ui.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
 * panel does not wait for them.
 */
public final class Ink {

    /** The weights of Inter ash ships: the static TrueType files, since Java 2D cannot drive a variable font's axes. */
    public enum Weight {
        REGULAR("Inter-Regular.ttf"),
        SEMIBOLD("Inter-SemiBold.ttf"),
        BOLD("Inter-Bold.ttf"),
        EXTRABOLD("Inter-ExtraBold.ttf");

        private final String file;

        Weight(String file) {
            this.file = file;
        }
    }

    /** At most this many pixels are kept across every cached raster: 32 MB of ARGB. */
    private static final long MAX_CACHED_PIXELS = 8L * 1024 * 1024;

    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);

    private static FutureTask<Map<Weight, Font>> loading;
    private static final Map<String, Font> SIZED = new HashMap<>();
    private static long cachedPixels;
    private static final LinkedHashMap<String, Raster> CACHE = new LinkedHashMap<String, Raster>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Raster> eldest) {
            if (cachedPixels > MAX_CACHED_PIXELS) {
                cachedPixels -= (long) eldest.getValue().width() * eldest.getValue().height();
                return true;
            }
            return false;
        }
    };

    private Ink() {
    }

    /** Starts loading Inter on a thread of its own. Call once, at startup; calling again does nothing. */
    public static synchronized void preload() {
        if (loading == null) {
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
                    throw new IllegalStateException("Inter is missing from the client: " + weight.file);
                }
                fonts.put(weight, Font.createFont(Font.TRUETYPE_FONT, in));
            } catch (java.io.IOException | java.awt.FontFormatException unreadable) {
                throw new IllegalStateException("Inter could not be read: " + weight.file, unreadable);
            }
        }
        return fonts;
    }

    private static synchronized Font font(Weight weight, float size) {
        String key = weight + "/" + size;
        Font sized = SIZED.get(key);
        if (sized == null) {
            preload();
            try {
                sized = loading.get().get(weight).deriveFont(size);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while loading Inter", interrupted);
            } catch (ExecutionException failed) {
                throw new IllegalStateException("Inter could not be loaded", failed.getCause());
            }
            SIZED.put(key, sized);
        }
        return sized;
    }

    // ---- text ----

    /** How far {@code text} advances, in real pixels, rounded up. */
    public static int width(String text, Weight weight, float size) {
        return (int) Math.ceil(font(weight, size).getStringBounds(text, METRICS).getWidth());
    }

    /** The height of one line, ascent and descent, in real pixels, rounded up. */
    public static int lineHeight(Weight weight, float size) {
        LineMetrics line = font(weight, size).getLineMetrics("Hg", METRICS);
        return (int) Math.ceil(line.getAscent() + line.getDescent());
    }

    /** The height of a capital letter, for centring a line of capitals by eye rather than by its descent. */
    public static int capHeight(Weight weight, float size) {
        return (int) Math.round(font(weight, size).createGlyphVector(METRICS, "H").getVisualBounds().getHeight());
    }

    /**
     * One line of text, its origin at the top-left of its line box: draw it
     * with {@link Raster#drawAt}. It has a margin all round, so that a glyph
     * reaching outside its advance is not cut off.
     */
    public static synchronized Raster text(String text, Weight weight, float size, int argb) {
        String key = "text/" + weight + "/" + size + "/" + Integer.toHexString(argb) + "/" + text;
        Raster cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = font(weight, size);
        LineMetrics line = font.getLineMetrics(text.isEmpty() ? "H" : text, METRICS);
        int margin = (int) Math.ceil(size * 0.25f) + 1;
        int w = width(text, weight, size) + 2 * margin;
        int h = (int) Math.ceil(line.getAscent() + line.getDescent()) + 2 * margin;
        BufferedImage image = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        g.setFont(font);
        g.setColor(new Color(argb, true));
        g.drawString(text, (float) margin, margin + line.getAscent());
        g.dispose();
        return remember(key, image, margin, margin, text);
    }

    // ---- shapes ----

    /**
     * One corner of a rounded rectangle, anti-aliased: a quarter disc of
     * radius {@code radius}, in the corner {@code quadrant} - 0 top-left, 1
     * top-right, 2 bottom-right, 3 bottom-left. Computed by sampling, not by
     * Java 2D, so that the edge is the same on every runtime.
     */
    public static synchronized Raster corner(int radius, int quadrant, int argb) {
        String key = "corner/" + radius + "/" + quadrant + "/" + Integer.toHexString(argb);
        Raster cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int[] pixels = new int[radius * radius];
        int alpha = argb >>> 24;
        int samples = 4;
        for (int y = 0; y < radius; y++) {
            for (int x = 0; x < radius; x++) {
                int inside = 0;
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        // Distance from the disc's centre, which sits on the
                        // inner corner of this quadrant.
                        double px = x + (sx + 0.5) / samples;
                        double py = y + (sy + 0.5) / samples;
                        double dx = (quadrant == 0 || quadrant == 3) ? radius - px : px;
                        double dy = (quadrant == 0 || quadrant == 1) ? radius - py : py;
                        if (dx * dx + dy * dy <= (double) radius * radius) {
                            inside++;
                        }
                    }
                }
                int a = Math.round(alpha * inside / (float) (samples * samples));
                pixels[y * radius + x] = (a << 24) | (argb & 0xFFFFFF);
            }
        }
        return remember(key, radius, radius, pixels);
    }

    /**
     * The soft shadow around a rounded rectangle, as nine pieces: four
     * corners, and four one-pixel strips stretched along the edges. Only
     * outside the rectangle: inside is left to whatever is drawn there.
     *
     * @param piece 0-3 the corners as in {@link #corner}; 4 top, 5 right, 6 bottom, 7 left
     * @param reach how far the shadow fades out to, past the rectangle's edge
     */
    public static synchronized Raster shadow(int radius, int reach, int alpha, int piece) {
        String key = "shadow/" + radius + "/" + reach + "/" + alpha + "/" + piece;
        Raster cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int size = radius + reach;
        if (piece < 4) {
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double dx = (piece == 0 || piece == 3) ? size - (x + 0.5) : x + 0.5;
                    double dy = (piece == 0 || piece == 1) ? size - (y + 0.5) : y + 0.5;
                    pixels[y * size + x] = shadowAt(Math.sqrt(dx * dx + dy * dy) - radius, reach, alpha);
                }
            }
            return remember(key, size, size, pixels);
        }
        boolean horizontal = piece == 4 || piece == 6;
        int[] pixels = new int[reach];
        for (int i = 0; i < reach; i++) {
            // Distance out from the edge, 0.5 at the pixel touching it.
            double out = (piece == 4 || piece == 7) ? reach - (i + 0.5) : i + 0.5;
            pixels[i] = shadowAt(out, reach, alpha);
        }
        return horizontal ? remember(key, 1, reach, pixels) : remember(key, reach, 1, pixels);
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
     * A stand-in outline icon, drawn on a 24-unit grid with round strokes,
     * until #65 brings Lucide's set: "gear" and "layout".
     */
    public static synchronized Raster icon(String name, int size, int argb) {
        String key = "icon/" + name + "/" + size + "/" + Integer.toHexString(argb);
        Raster cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(image);
        g.setColor(new Color(argb, true));
        g.scale(size / 24.0, size / 24.0);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (name.equals("gear")) {
            g.draw(gear());
            g.draw(new Ellipse2D.Double(9, 9, 6, 6));
        } else if (name.equals("layout")) {
            g.draw(new RoundRectangle2D.Double(3, 3, 7, 9, 3, 3));
            g.draw(new RoundRectangle2D.Double(14, 3, 7, 5, 3, 3));
            g.draw(new RoundRectangle2D.Double(14, 12, 7, 9, 3, 3));
            g.draw(new RoundRectangle2D.Double(3, 16, 7, 5, 3, 3));
        } else {
            throw new IllegalArgumentException("no icon " + name);
        }
        g.dispose();
        return remember(key, image, 0, 0, null);
    }

    /** A cog of eight rounded teeth about the grid's centre. */
    private static Shape gear() {
        Path2D.Double path = new Path2D.Double();
        int teeth = 8;
        for (int i = 0; i < teeth * 2; i++) {
            double angle = Math.PI * i / teeth;
            double outer = i % 2 == 0 ? 10 : 7.6;
            double half = Math.PI / teeth * 0.42;
            double a0 = angle - half;
            double a1 = angle + half;
            double x0 = 12 + outer * Math.cos(a0);
            double y0 = 12 + outer * Math.sin(a0);
            if (i == 0) {
                path.moveTo(x0, y0);
            } else {
                path.lineTo(x0, y0);
            }
            path.lineTo(12 + outer * Math.cos(a1), 12 + outer * Math.sin(a1));
        }
        path.closePath();
        return AffineTransform.getRotateInstance(Math.PI / 16, 12, 12).createTransformedShape(path);
    }

    // ---- the cache ----

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static Raster remember(String key, BufferedImage image, int originX, int originY, String text) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Raster raster = new Raster(image.getWidth(), image.getHeight(), pixels, originX, originY, text);
        return remember(key, raster);
    }

    private static Raster remember(String key, int width, int height, int[] pixels) {
        return remember(key, new Raster(width, height, pixels, 0, 0, null));
    }

    private static Raster remember(String key, Raster raster) {
        cachedPixels += (long) raster.width() * raster.height();
        CACHE.put(key, raster);
        return raster;
    }
}
