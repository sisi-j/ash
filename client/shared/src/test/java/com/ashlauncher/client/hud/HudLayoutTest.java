package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ashlauncher.client.game.FakeGameView;
import com.ashlauncher.client.settings.Settings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The HUD items' registry, and where the layout puts an item left at its home (spec 0007, HUD items). */
class HudLayoutTest {

    @TempDir
    Path configDir;

    @Test
    void every_item_is_declared_once_with_its_feature_home_label_sample_and_style_options() {
        assertEquals(List.of(HudItem.FPS, HudItem.PING), HudItem.ALL);
        for (HudItem item : HudItem.ALL) {
            assertNotNull(item.feature(), item + " has no feature");
            assertNotNull(item.home(), item + " has no home");
            assertFalse(item.label().isEmpty(), item + " has no label");
            assertNotNull(item.sample(), item + " has nothing to show in Edit HUD");
            assertFalse(item.styles().isEmpty(), item + " takes no style options");
        }
        // The readouts keep their settings, keys and all, so a position a
        // player saved before the registry means what it meant.
        assertEquals("fps-readout.position", HudItem.FPS.position().key());
        assertEquals("ping-readout.position", HudItem.PING.position().key());
        assertEquals(new Placement(Anchor.TOP_LEFT, 4, 4), HudItem.FPS.home());
        assertEquals(new Placement(Anchor.TOP_LEFT, 4, 15), HudItem.PING.home());
    }

    @Test
    void what_an_item_shows_comes_from_the_game_view() {
        FakeGameView game = new FakeGameView().withFramesPerSecond(144).withLatency(42);

        assertEquals("144", HudItem.FPS.value(game));
        assertEquals("42", HudItem.PING.value(game));
        assertEquals(null, HudItem.PING.value(new FakeGameView()), "the ping showed in singleplayer");
        assertEquals(null, HudItem.PING.value(new FakeGameView().withLatency(-1)));
    }

    @Test
    void an_item_at_home_that_would_overlap_one_the_player_placed_moves_down_its_column_past_it() {
        Settings settings = Settings.load(configDir);
        // The frame rate, moved just below its home: in the way of the ping,
        // which takes the frame rate's place when the frame rate has moved.
        settings.set(Settings.FPS_READOUT_POSITION, new Placement(Anchor.TOP_LEFT, 4, 6));
        HudItems items = new HudItems(new HudLayout(settings),
                new FakeGameView().withFramesPerSecond(144).withLatency(42));
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        items.draw(surface);

        FakeHudSurface.Text fps = surface.drawn().get(0);
        FakeHudSurface.Text ping = surface.drawn().get(1);
        assertEquals(6, fps.y(), "the frame rate is not where the player put it");
        assertEquals(4, ping.x());
        assertEquals(6 + 9 + HudLayout.GAP, ping.y(), "the ping was not moved past the frame rate");
    }

    @Test
    void the_first_frame_already_stacks_the_ping_beneath_the_frame_rate() {
        // Before anything has drawn, nothing's size is known; the homes' own
        // spacing stands in, so the ping never starts on top of the frame rate.
        HudLayout layout = new HudLayout(Settings.load(configDir));

        assertEquals(new Placement(Anchor.TOP_LEFT, 4, 15), layout.placement(HudItem.PING));
    }
}
