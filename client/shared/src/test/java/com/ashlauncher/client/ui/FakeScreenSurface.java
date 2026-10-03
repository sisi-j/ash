package com.ashlauncher.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A screen with no game behind it: records every rectangle and every line of
 * text, measures text as the game's own font roughly does - six units a
 * character, nine a line - and answers what is at a point.
 */
final class FakeScreenSurface implements ScreenSurface {

    /** One line of text, as it arrived. */
    record Text(String text, int x, int y, int colour) {
    }

    /** One rectangle, as it arrived. */
    record Fill(int x, int y, int width, int height, int colour) {
    }

    final List<Text> texts = new ArrayList<>();
    final List<Fill> fills = new ArrayList<>();

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        fills.add(new Fill(x, y, width, height, colour));
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        texts.add(new Text(text, x, y, colour));
    }

    @Override
    public int textWidth(String text) {
        return text.length() * 6;
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    /** Every line drawn, as strings, in order. */
    List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Text text : texts) {
            lines.add(text.text());
        }
        return lines;
    }

    boolean drew(String text) {
        return lines().contains(text);
    }

    Text find(String text) {
        for (Text drawn : texts) {
            if (drawn.text().equals(text)) {
                return drawn;
            }
        }
        throw new AssertionError("\"" + text + "\" was not drawn; drew " + lines());
    }
}
