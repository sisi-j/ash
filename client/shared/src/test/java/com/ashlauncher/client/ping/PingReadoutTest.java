package com.ashlauncher.client.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.FakeHudSurface;
import org.junit.jupiter.api.Test;

class PingReadoutTest {

    @Test
    void it_says_the_servers_number_in_milliseconds() {
        assertEquals("42 ms", PingReadout.text(42));
        assertEquals("0 ms", PingReadout.text(0));
    }

    @Test
    void it_sits_beneath_the_fps_readout_and_never_overlaps_it() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, () -> true).draw(surface);
        new PingReadout(() -> 42, () -> true, () -> true).draw(surface);

        FakeHudSurface.Text fps = surface.drawn().get(0);
        FakeHudSurface.Text ping = surface.drawn().get(1);
        assertEquals("42 ms", ping.text());
        assertEquals(fps.x(), ping.x());
        assertTrue(ping.y() >= fps.y() + surface.lineHeight(), "the two lines overlap: " + fps + " " + ping);
    }

    @Test
    void with_the_fps_readout_off_it_takes_its_place() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, () -> false).draw(surface);
        new PingReadout(() -> 42, () -> true, () -> false).draw(surface);

        assertEquals(PingReadout.MARGIN, surface.onlyText().y());
    }

    @Test
    void while_the_player_hosts_the_world_it_is_hidden() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> null, () -> true, () -> true).draw(surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void a_latency_the_server_has_not_given_shows_nothing() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> -1, () -> true, () -> true).draw(surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void it_steps_aside_for_the_debug_screen_a_hidden_hud_and_its_switch() {
        for (FakeHudSurface surface : new FakeHudSurface[] {
            FakeHudSurface.ofTypicalSize().withDebugScreenShown(), FakeHudSurface.ofTypicalSize().withHudHidden()}) {
            new PingReadout(() -> 42, () -> true, () -> true).draw(surface);
            assertTrue(surface.drawn().isEmpty());
        }
        FakeHudSurface off = FakeHudSurface.ofTypicalSize();
        new PingReadout(() -> 42, () -> false, () -> true).draw(off);
        assertTrue(off.drawn().isEmpty());
    }
}
