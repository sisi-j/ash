package com.ashlauncher.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitting text into the room it has, measured by the surface - so it fits in
 * whatever font the surface draws.
 */
final class Text {

    private static final String MORE = "...";

    private Text() {
    }

    /** {@code text} if it fits in {@code room}, otherwise as much as fits with "..." after it. */
    static String fit(ScreenSurface surface, String text, int room) {
        if (surface.textWidth(text) <= room) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && surface.textWidth(cut + MORE) > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + MORE;
    }

    /** The end of {@code text} that fits in {@code room}: what was typed last stays in view. */
    static String tail(ScreenSurface surface, String text, int room) {
        String shown = text;
        while (!shown.isEmpty() && surface.textWidth(shown) > room) {
            shown = shown.substring(shown.offsetByCodePoints(0, 1));
        }
        return shown;
    }

    /**
     * {@code text} broken at spaces into lines no wider than {@code room}, at
     * most {@code maxLines} of them; the last ends in "..." when there was
     * more to say.
     */
    static List<String> wrap(ScreenSurface surface, String text, int room, int maxLines) {
        List<String> lines = new ArrayList<>();
        String line = "";
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (surface.textWidth(candidate) <= room || line.isEmpty()) {
                line = candidate;
                continue;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(fit(surface, line + " " + join(words, i), room));
                return lines;
            }
            // Fitted too: a single word wider than the room is taken whole
            // above, and has to be cut here rather than run off the card.
            lines.add(fit(surface, line, room));
            line = words[i];
        }
        if (!line.isEmpty()) {
            lines.add(fit(surface, line, room));
        }
        return lines;
    }

    private static String join(String[] words, int from) {
        StringBuilder rest = new StringBuilder();
        for (int i = from; i < words.length; i++) {
            if (rest.length() > 0) {
                rest.append(' ');
            }
            rest.append(words[i]);
        }
        return rest.toString();
    }
}
