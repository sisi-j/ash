package com.ashlauncher.client.fps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.hud.FakeHudSurface;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FpsReadoutTest {

    @Test
    void it_shows_the_frame_rate_the_game_reports() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals("144 FPS", surface.onlyText().text());
    }

    @Test
    void it_asks_the_game_again_every_frame() {
        // Read once and held, the number would be whatever the frame rate was
        // at the moment the world loaded, for the rest of the session.
        AtomicInteger rate = new AtomicInteger(60);
        FpsReadout readout = new FpsReadout(rate::get, true);

        FakeHudSurface first = FakeHudSurface.ofTypicalSize();
        readout.draw(first);
        rate.set(30);
        FakeHudSurface second = FakeHudSurface.ofTypicalSize();
        readout.draw(second);

        assertEquals("60 FPS", first.onlyText().text());
        assertEquals("30 FPS", second.onlyText().text());
    }

    /**
     * The smallest GUI either target will draw. Both size the GUI scale so the
     * scaled screen is never shorter than 240, whatever scale the player picks
     * - so a layout that is clear here is clear everywhere.
     */
    private static final int SMALLEST_GUI_HEIGHT = 240;

    /**
     * Everything vanilla anchors to the bottom of the screen, on both targets:
     * the hotbar and the status bars above it, and the chat above those - 90
     * high when it is not focused, its bottom 48 above the screen's on 1.8.9
     * and 40 on 1.21.11. The higher of the two.
     *
     * <p>Open chat is not in this and could not honestly be. It is 180 high at
     * the default setting (1.8.9's {@code ChatHud.getHeight}: 160 times the
     * setting, plus 20), so at this smallest GUI - which is what a 1280 by
     * 720 window gets at auto scale - a full 20 lines of it on 1.8.9 reaches
     * row 12, the readout's drop shadow. One row, while the player is typing,
     * on the smallest GUI either target draws. Clear everywhere else.
     */
    private static final int BOTTOM_ANCHORED_HUD_TOP = SMALLEST_GUI_HEIGHT - 48 - 90;

    /** The crosshair is 15 by 15, centred. */
    private static final int CROSSHAIR_TOP = SMALLEST_GUI_HEIGHT / 2 - 7;

    @Test
    void it_sits_in_the_top_left_clear_of_the_crosshair_the_hotbar_and_the_chat() {
        FakeHudSurface surface = new FakeHudSurface(SMALLEST_GUI_HEIGHT, 9);

        new FpsReadout(() -> 144, true).draw(surface);

        FakeHudSurface.Text drawn = surface.onlyText();
        int bottom = drawn.y() + surface.lineHeight();
        assertTrue(bottom < CROSSHAIR_TOP, "the readout reaches the crosshair");
        assertTrue(bottom < BOTTOM_ANCHORED_HUD_TOP, "the readout reaches the chat or the hotbar");
        // Inset from the corner rather than on it: at some GUI scales the
        // game crops a pixel or two at the very edge.
        assertTrue(drawn.x() >= 2 && drawn.y() >= 2, "the readout is on the edge of the screen");
    }

    @Test
    void a_player_who_turned_it_off_sees_nothing() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, false).draw(surface);

        assertEquals(0, surface.drawn().size(), "drew " + surface.drawn());
    }

    @Test
    void it_steps_aside_while_the_debug_screen_is_up() {
        // The debug screen draws its own frame rate from the same corner, and
        // two lines of text on top of each other read as neither.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize().withDebugScreenShown();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals(0, surface.drawn().size(), "drew " + surface.drawn());
    }

    @Test
    void it_is_hidden_with_the_rest_of_the_hud() {
        // F1, usually for a screenshot. 1.21.11's registry asks ash to draw
        // under F1 anyway - `addLast` inherits no render condition - and 1.8.9
        // asks too while a menu is open. The readout has to answer the same on
        // both, so the answer lives here rather than in either game.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize().withHudHidden();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals(0, surface.drawn().size(), "drew " + surface.drawn());
    }

    @Test
    void it_is_opaque() {
        // At zero alpha 1.21.11 draws nothing - a readout switched on and
        // invisible - and 1.8.9 draws it opaque, so it would not even fail the
        // same way on both.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals(0xFF, (surface.onlyText().colour() >>> 24) & 0xFF);
    }
}
