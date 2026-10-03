package com.ashlauncher.client.spike;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * THROWAWAY spike for research 0007 (branch {@code spike/final-drawing}):
 * a representative slice of the approved panel drawn with Java 2D and Inter,
 * at the screen's real resolution. Each target uploads the image and draws it
 * 1:1 over the game's own blur, so the spike shows whether Java 2D works
 * inside each game's JVM, how crisp the result is, and what it costs.
 */
public final class SpikePanel {

    private static Font regular;
    private static Font semibold;
    private static Font bold;
    private static Font extrabold;

    /** How long the last {@link #render} took, for the research write-up. */
    public static volatile long lastRenderNanos;

    private SpikePanel() {
    }

    private static Font load(String name) {
        try (InputStream in = SpikePanel.class.getResourceAsStream("/assets/ash/fonts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no font " + name + " on the class path");
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception failed) {
            throw new IllegalStateException("could not load " + name, failed);
        }
    }

    private static synchronized void loadFonts() {
        if (regular == null) {
            regular = load("Inter-Regular.ttf");
            semibold = load("Inter-SemiBold.ttf");
            bold = load("Inter-Bold.ttf");
            extrabold = load("Inter-ExtraBold.ttf");
        }
    }

    /** The whole screen, transparent outside the panel, in ARGB. */
    public static BufferedImage render(int width, int height) {
        long started = System.nanoTime();
        loadFonts();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double u = width / 100.0;
        double px = width * 0.075;
        double py = height * 0.075;
        double pw = width * 0.85;
        double ph = height * 0.85;
        RoundRectangle2D panel = new RoundRectangle2D.Double(px, py, pw, ph, 2.2 * u, 2.2 * u);
        g.setColor(new Color(0, 0, 0, 140));
        g.fill(panel);

        // The strip: darker, divided by a thin line.
        g.setClip(panel);
        double sw = 5.2 * u;
        g.setColor(new Color(0, 0, 0, 92));
        g.fill(new java.awt.geom.Rectangle2D.Double(px, py, sw, ph));
        g.setColor(new Color(255, 255, 255, 23));
        g.draw(new Line2D.Double(px + sw, py, px + sw, py + ph));
        g.setClip(null);

        g.setColor(Color.WHITE);
        g.setFont(extrabold.deriveFont((float) (2.2 * u)));
        FontMetrics letters = g.getFontMetrics();
        double y = py + 1.3 * u + letters.getAscent();
        for (char c : "SETTINGS".toCharArray()) {
            String s = String.valueOf(c);
            g.drawString(s, (float) (px + (sw - letters.stringWidth(s)) / 2), (float) y);
            y += letters.getAscent() + 0.5 * u;
        }
        double tool = sw - 1.5 * u;
        for (int i = 0; i < 2; i++) {
            double ty = py + ph - 0.75 * u - (2 - i) * tool - (1 - i) * 0.6 * u;
            g.setColor(new Color(255, 255, 255, i == 0 ? 7 : 15));
            g.fill(new RoundRectangle2D.Double(px + 0.75 * u, ty, tool, tool, 1.3 * u, 1.3 * u));
            g.setColor(new Color(255, 255, 255, i == 0 ? 85 : 204));
            g.setStroke(new BasicStroke((float) (0.17 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Double(px + 0.75 * u + tool * 0.3, ty + tool * 0.3, tool * 0.4, tool * 0.4));
        }

        // The tiles area.
        double mx = px + sw + 1.5 * u;
        double my = py + 1.3 * u;
        double mw = pw - sw - 3 * u;
        g.setColor(new Color(255, 255, 255, 15));
        g.fill(new RoundRectangle2D.Double(mx, my, 19 * u, 2.5 * u, 1.4 * u, 1.4 * u));
        g.setFont(regular.deriveFont((float) (0.92 * u)));
        g.setColor(new Color(255, 255, 255, 102));
        g.drawString("Search features", (float) (mx + 2.3 * u), (float) (my + 1.6 * u));
        String[] tabs = {"All", "PvP", "HUD", "Movement", "Performance"};
        g.setFont(semibold.deriveFont((float) (0.88 * u)));
        FontMetrics tm = g.getFontMetrics();
        double tx = mx + mw;
        for (int i = tabs.length - 1; i >= 0; i--) {
            double w = tm.stringWidth(tabs[i]) + 1.9 * u;
            tx -= w + 0.35 * u;
            if (i == 0) {
                g.setColor(new Color(255, 255, 255, 26));
                g.fill(new RoundRectangle2D.Double(tx, my + 0.3 * u, w, 1.9 * u, 1.2 * u, 1.2 * u));
            }
            g.setColor(i == 0 ? Color.WHITE : new Color(255, 255, 255, 163));
            g.drawString(tabs[i], (float) (tx + 0.95 * u), (float) (my + 1.6 * u));
        }

        String[] names = {"Crosshair", "Hit indicator", "Hit colour", "FPS readout", "Ping readout",
            "Toggle sprint", "Freelook", "Snaplook", "Entity culling", "Motion blur"};
        int[] fps = {0, 0, 0, 0, 0, 0, 0, 0, 1, -1};
        boolean[] on = {true, true, false, true, true, true, false, false, true, false};
        double gap = 1 * u;
        double tw = (mw - 4 * gap) / 5;
        double th = 14 * u;
        for (int i = 0; i < names.length; i++) {
            double x = mx + (i % 5) * (tw + gap);
            double ty = my + 3.7 * u + (i / 5) * (th + gap);
            g.setColor(new Color(255, 255, 255, 15));
            g.fill(new RoundRectangle2D.Double(x, ty, tw, th, 1.8 * u, 1.8 * u));
            g.setColor(Color.WHITE);
            g.setFont(semibold.deriveFont((float) (0.95 * u)));
            FontMetrics nm = g.getFontMetrics();
            g.drawString(names[i], (float) (x + (tw - nm.stringWidth(names[i])) / 2), (float) (ty + 0.95 * u + nm.getAscent()));
            g.setColor(new Color(255, 255, 255, 204));
            g.setStroke(new BasicStroke((float) (0.2 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double ic = 3.2 * u;
            g.draw(new Ellipse2D.Double(x + (tw - ic) / 2 + 0.35 * u, ty + 3.4 * u, ic - 0.7 * u, ic - 0.7 * u));
            double cx = x + tw / 2;
            double fy = ty + 7.5 * u;
            if (fps[i] == 0) {
                g.setColor(new Color(167, 173, 166));
                g.fill(new RoundRectangle2D.Double(cx - 0.72 * u, fy + 0.35 * u, 1.44 * u, 0.26 * u, 0.26 * u, 0.26 * u));
            } else {
                g.setColor(fps[i] > 0 ? new Color(34, 197, 94) : new Color(240, 67, 58));
                Polygon tri = new Polygon();
                int top = (int) Math.round(fy);
                int bottom = (int) Math.round(fy + 0.85 * u);
                int half = (int) Math.round(0.62 * u);
                if (fps[i] > 0) {
                    tri.addPoint((int) Math.round(cx), top);
                    tri.addPoint((int) Math.round(cx + half), bottom);
                    tri.addPoint((int) Math.round(cx - half), bottom);
                } else {
                    tri.addPoint((int) Math.round(cx), bottom);
                    tri.addPoint((int) Math.round(cx + half), top);
                    tri.addPoint((int) Math.round(cx - half), top);
                }
                g.setStroke(new BasicStroke((float) (0.19 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.fill(tri);
                g.draw(tri);
            }
            g.setFont(bold.deriveFont((float) (0.74 * u)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(255, 255, 255, 191));
            g.drawString("FPS", (float) (cx - fm.stringWidth("FPS") / 2.0), (float) (fy + 1.25 * u + fm.getAscent()));
            double by = ty + th - 0.85 * u - 2.3 * u;
            g.setColor(new Color(255, 255, 255, 15));
            g.fill(new RoundRectangle2D.Double(x + 0.9 * u, by, 2.3 * u, 2.3 * u, 1.1 * u, 1.1 * u));
            boolean unavailable = i == 7;
            g.setColor(unavailable ? new Color(167, 173, 166, 89) : on[i] ? new Color(34, 197, 94) : new Color(240, 67, 58));
            double bx = x + 0.9 * u + 2.3 * u + 0.45 * u;
            double bw = tw - 1.8 * u - 2.75 * u;
            g.fill(new RoundRectangle2D.Double(bx, by, bw, 2.3 * u, 1.1 * u, 1.1 * u));
            String label = unavailable ? "UNAVAILABLE" : on[i] ? "ENABLED" : "DISABLED";
            g.setFont(bold.deriveFont((float) (0.74 * u)));
            FontMetrics lm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(label, (float) (bx + (bw - lm.stringWidth(label)) / 2), (float) (by + (2.3 * u + lm.getAscent() - lm.getDescent()) / 2));
        }
        g.dispose();
        lastRenderNanos = System.nanoTime() - started;
        return image;
    }
}
