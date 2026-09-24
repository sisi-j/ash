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

    private final int height;
    private final int lineHeight;
    private final List<Text> drawn = new ArrayList<>();
    private boolean debugScreenShown;
    private boolean hudHidden;

    public FakeHudSurface(int height, int lineHeight) {
        this.height = height;
        this.lineHeight = lineHeight;
    }

    /** A surface roughly the size of a real one at the default GUI scale. */
    public static FakeHudSurface ofTypicalSize() {
        return new FakeHudSurface(240, 9);
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

    /** The only thing drawn, failing the test if that is not what happened. */
    public Text onlyText() {
        if (drawn.size() != 1) {
            throw new AssertionError("expected exactly one draw, got " + drawn);
        }
        return drawn.get(0);
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
    public boolean debugScreenShown() {
        return debugScreenShown;
    }

    @Override
    public boolean hudHidden() {
        return hudHidden;
    }
}
