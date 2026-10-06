package com.ashlauncher.client.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Anchor;
import com.ashlauncher.client.hud.FakeHudSurface;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.Placement;
import com.ashlauncher.client.settings.Settings;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PingReadoutTest {

    @TempDir
    Path configDir;

    @Test
    void it_says_the_servers_number_in_milliseconds() {
        assertEquals("42 ms", PingReadout.text(42));
        assertEquals("0 ms", PingReadout.text(0));
    }

    @Test
    void it_sits_beneath_the_fps_readout_and_never_overlaps_it() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new FpsReadout(() -> 144, layout).draw(surface);
        new PingReadout(() -> 42, layout).draw(surface);

        FakeHudSurface.Text fps = surface.drawn().get(0);
        FakeHudSurface.Text ping = surface.drawn().get(1);
        assertEquals("42 ms", ping.text());
        assertEquals(fps.x(), ping.x());
        assertTrue(ping.y() >= fps.y() + surface.lineHeight(), "the two lines overlap: " + fps + " " + ping);
    }

    @Test
    void with_the_fps_readout_off_it_takes_its_place() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.FPS_READOUT, false);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> 42, new HudLayout(settings)).draw(surface);

        assertEquals(4, surface.onlyText().x());
        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void with_the_fps_readout_moved_away_it_takes_its_place() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.FPS_READOUT_POSITION, new Placement(Anchor.BOTTOM_RIGHT, 4, 4));
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> 42, new HudLayout(settings)).draw(surface);

        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void once_moved_it_stays_where_it_was_put_whatever_the_fps_readout_does() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.PING_READOUT_POSITION, new Placement(Anchor.TOP_RIGHT, 4, 4));
        settings.set(Settings.FPS_READOUT, false);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> 42, new HudLayout(settings)).draw(surface);

        // 427 wide, "42 ms" five characters of six.
        assertEquals(427 - 30 - 4, surface.onlyText().x());
        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void while_the_player_hosts_the_world_it_is_hidden() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> null, new HudLayout(Settings.load(configDir))).draw(surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void in_edit_hud_it_shows_without_a_number_so_it_can_still_be_moved() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        layout.setEditing(true);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> null, layout).draw(surface);

        assertEquals(PingReadout.NO_NUMBER, surface.onlyText().text());
        assertEquals(30, layout.width(HudLayout.Readout.PING), "Edit HUD outlines what it drew");
    }

    @Test
    void a_latency_the_server_has_not_given_shows_nothing() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new PingReadout(() -> -1, new HudLayout(Settings.load(configDir))).draw(surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void it_steps_aside_for_the_debug_screen_a_hidden_hud_and_its_switch() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        for (FakeHudSurface surface : new FakeHudSurface[] {
            FakeHudSurface.ofTypicalSize().withDebugScreenShown(), FakeHudSurface.ofTypicalSize().withHudHidden()}) {
            new PingReadout(() -> 42, layout).draw(surface);
            assertTrue(surface.drawn().isEmpty());
        }
        Settings settings = Settings.load(configDir);
        settings.set(Settings.PING_READOUT, false);
        FakeHudSurface off = FakeHudSurface.ofTypicalSize();
        new PingReadout(() -> 42, new HudLayout(settings)).draw(off);
        assertTrue(off.drawn().isEmpty());
    }
}
