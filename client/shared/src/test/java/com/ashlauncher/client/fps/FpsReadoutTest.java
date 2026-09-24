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
     * the hotbar and the status bars above it, and the chat above those - ten
     * lines of nine when it is not focused, its bottom 48 above the screen's
     * on 1.8.9 and 40 on 1.21.11. The higher of the two, rounded up.
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
        // F1, usually for a screenshot. On 1.8.9 the game never asks ash to
        // draw at all while the HUD is hidden; on 1.21.11 Fabric's `addLast`
        // does not inherit the HUD's render condition and asks anyway. The
        // readout has to answer the same on both, so it answers here.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize().withHudHidden();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals(0, surface.drawn().size(), "drew " + surface.drawn());
    }

    @Test
    void it_is_opaque() {
        // A zero alpha draws nothing on either target - a readout that is
        // switched on and invisible.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, true).draw(surface);

        assertEquals(0xFF, (surface.onlyText().colour() >>> 24) & 0xFF);
    }
}
