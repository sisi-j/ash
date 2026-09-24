package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkerTest {

    @Test
    void it_draws_the_marker_a_margin_in_from_the_bottom_left() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new Marker().draw(surface);

        FakeHudSurface.Text drawn = surface.onlyText();
        assertEquals(Marker.TEXT, drawn.text());
        assertEquals(Marker.MARGIN, drawn.x());
        assertEquals(240 - 9 - Marker.MARGIN, drawn.y());
    }

    @Test
    void it_follows_the_bottom_of_the_surface_rather_than_a_fixed_height() {
        // The window is resizable and the GUI scale is the player's to change,
        // so a marker placed at a remembered height leaves the screen.
        FakeHudSurface small = new FakeHudSurface(120, 9);
        FakeHudSurface large = new FakeHudSurface(480, 9);

        new Marker().draw(small);
        new Marker().draw(large);

        assertTrue(small.onlyText().y() < large.onlyText().y(),
                "the marker did not move with the bottom of the surface");
    }

    @Test
    void it_sits_clear_of_the_bottom_edge_and_draws_nothing_else() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new Marker().draw(surface);

        FakeHudSurface.Text drawn = surface.onlyText();
        assertTrue(drawn.y() >= 0, "the marker was drawn off the top of the surface");
        assertTrue(drawn.y() + surface.lineHeight() <= surface.height(),
                "the marker was drawn off the bottom of the surface");
        assertEquals(1, surface.drawn().size());
    }

    @Test
    void the_marker_is_opaque() {
        // A zero alpha draws nothing at all on either target, which would look
        // exactly like the client having failed to load.
        assertEquals(0xFF, (Marker.COLOUR >>> 24) & 0xFF);
    }

    @Test
    void the_marker_is_hidden_with_the_rest_of_the_hud() {
        // The same asymmetry as the FPS readout: 1.21.11 would draw it
        // through F1 and 1.8.9 would not.
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize().withHudHidden();

        new Marker().draw(surface);

        assertEquals(0, surface.drawn().size(), "drew " + surface.drawn());
    }
}
