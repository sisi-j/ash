package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.game.FakeGameView;
import com.ashlauncher.client.settings.Settings;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ping readout, the second HUD item: drawn from the registry, as the game draws it. */
class PingReadoutTest {

    /** Draws the ping item, in a game where the server reports {@code ms}. */
    private static void ping(Integer ms, HudLayout layout, FakeHudSurface surface) {
        new HudItems(layout, new FakeGameView().withLatency(ms)).draw(HudItem.PING, surface);
    }

    private static void fps(int rate, HudLayout layout, FakeHudSurface surface) {
        new HudItems(layout, new FakeGameView().withFramesPerSecond(rate)).draw(HudItem.FPS, surface);
    }

    @TempDir
    Path configDir;

    @Test
    void it_says_the_servers_number_in_milliseconds() {
        assertEquals("42 ms", HudItem.PING.text(HudItem.PING.value(new FakeGameView().withLatency(42))));
        assertEquals("0 ms", HudItem.PING.text(HudItem.PING.value(new FakeGameView().withLatency(0))));
    }

    @Test
    void it_sits_beneath_the_fps_readout_and_never_overlaps_it() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        fps(144, layout, surface);
        ping(42, layout, surface);

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

        ping(42, new HudLayout(settings), surface);

        assertEquals(4, surface.onlyText().x());
        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void with_the_fps_readout_moved_away_it_takes_its_place() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.FPS_READOUT_POSITION, new Placement(Anchor.BOTTOM_RIGHT, 4, 4));
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        ping(42, new HudLayout(settings), surface);

        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void once_moved_it_stays_where_it_was_put_whatever_the_fps_readout_does() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.PING_READOUT_POSITION, new Placement(Anchor.TOP_RIGHT, 4, 4));
        settings.set(Settings.FPS_READOUT, false);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        ping(42, new HudLayout(settings), surface);

        // 427 wide, "42 ms" five characters of six.
        assertEquals(427 - 30 - 4, surface.onlyText().x());
        assertEquals(4, surface.onlyText().y());
    }

    @Test
    void while_the_player_hosts_the_world_it_is_hidden() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        ping(null, new HudLayout(Settings.load(configDir)), surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void in_edit_hud_it_shows_without_a_number_so_it_can_still_be_moved() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        layout.setEditing(true);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        ping(null, layout, surface);

        assertEquals("-- ms", surface.onlyText().text());
        assertEquals(30, layout.width(HudItem.PING), "Edit HUD outlines what it drew");
    }

    @Test
    void a_latency_the_server_has_not_given_shows_nothing() {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        ping(-1, new HudLayout(Settings.load(configDir)), surface);

        assertTrue(surface.drawn().isEmpty());
    }

    @Test
    void it_steps_aside_for_the_debug_screen_a_hidden_hud_and_its_switch() {
        HudLayout layout = new HudLayout(Settings.load(configDir));
        for (FakeHudSurface surface : new FakeHudSurface[] {
            FakeHudSurface.ofTypicalSize().withDebugScreenShown(), FakeHudSurface.ofTypicalSize().withHudHidden()}) {
            ping(42, layout, surface);
            assertTrue(surface.drawn().isEmpty());
        }
        Settings settings = Settings.load(configDir);
        settings.set(Settings.PING_READOUT, false);
        FakeHudSurface off = FakeHudSurface.ofTypicalSize();
        ping(42, new HudLayout(settings), off);
        assertTrue(off.drawn().isEmpty());
    }
}
