package com.ashlauncher.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * A HUD that records what was drawn on it instead of drawing it.
 *
 * <p>The same bargain the launcher's {@code FakeHttp} makes: a feature is
 * driven through the interface it actually uses, and the test asks the fake
 * what it was told rather than reaching inside the feature. When a test needs
 * a situation this cannot produce - a surface of some other size, a font of
 * some other height - the fix is to widen the fake, never to reach around it.
 */
public final class FakeHudSurface implements HudSurface {

    /** One call to {@link HudSurface#drawText}, as it arrived. */
    public record Text(String text, int x, int y, int colour) {
    }

    /** One call to {@link HudSurface#fill}, as it arrived. */
    public record Fill(int x, int y, int width, int height, int colour) {

        boolean covers(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    /** What a pixel is when nothing was filled over it. */
    public static final int EMPTY = 0;

    private final int width;
    private final int height;
    private final int lineHeight;
    private final List<Text> drawn = new ArrayList<>();
    private final List<Fill> fills = new ArrayList<>();
    private boolean debugScreenShown;
    private boolean hudHidden;

    public FakeHudSurface(int width, int height, int lineHeight) {
        this.width = width;
        this.height = height;
        this.lineHeight = lineHeight;
    }

    /**
     * A surface the size of a real one: 1280 by 720 at GUI scale 3, which is
     * 427 wide - an odd width, so centring has to be got right.
     */
    public static FakeHudSurface ofTypicalSize() {
        return new FakeHudSurface(427, 240, 9);
    }

    /** The same surface with the debug screen open over it. */
    public FakeHudSurface withDebugScreenShown() {
        debugScreenShown = true;
        return this;
    }

    /** The same surface with the player having hidden the HUD. */
    public FakeHudSurface withHudHidden() {
        hudHidden = true;
        return this;
    }

    public List<Text> drawn() {
        return List.copyOf(drawn);
    }

    public List<Fill> fills() {
        return List.copyOf(fills);
    }

    /**
     * The colour a pixel ends up, painting the fills in the order they came -
     * so a test asks what the player sees, not which calls were made.
     */
    public int pixelAt(int x, int y) {
        int colour = EMPTY;
        for (Fill fill : fills) {
            if (fill.covers(x, y)) {
                colour = fill.colour();
            }
        }
        return colour;
    }

    /** The only thing drawn, failing the test if that is not what happened. */
    public Text onlyText() {
        if (drawn.size() != 1) {
            throw new AssertionError("expected exactly one draw, got " + drawn);
        }
        return drawn.get(0);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int lineHeight() {
        return lineHeight;
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        drawn.add(new Text(text, x, y, colour));
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        fills.add(new Fill(x, y, width, height, colour));
    }

    @Override
    public boolean debugScreenShown() {
        return debugScreenShown;
    }

    @Override
    public boolean hudHidden() {
        return hudHidden;
    }
}
