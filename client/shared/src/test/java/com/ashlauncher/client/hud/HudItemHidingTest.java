package com.ashlauncher.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.game.FakeGameView;
import com.ashlauncher.client.settings.Settings;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When a HUD item steps aside: for F3 or chat, as the player says; with
 * nothing to show, if it is only when relevant; and for F1, always (spec
 * 0007, The HUD editor). What is open is read from the game view.
 */
class HudItemHidingTest {

    @TempDir
    Path configDir;

    private Settings settings;

    private Settings settings() {
        if (settings == null) {
            settings = Settings.load(configDir);
        }
        return settings;
    }

    /** What an item draws in this game, in a test build. */
    private FakeHudSurface drawn(HudItem item, FakeGameView game) {
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();
        new HudItems(new HudLayout(settings()), game, true, () -> 0L).draw(item, surface);
        return surface;
    }

    private static FakeGameView playing() {
        return new FakeGameView().withFramesPerSecond(144).withLatency(42);
    }

    @Test
    void the_readouts_step_aside_for_f3_unless_the_player_says_otherwise() {
        // F3's debug text, frame rate and all, fills the readouts' corner.
        assertTrue(drawn(HudItem.FPS, playing().withDebugScreenShown()).drawn().isEmpty());

        settings().set(Settings.FPS_READOUT_ITEM.hideWithDebug, false);

        assertEquals("144 FPS", drawn(HudItem.FPS, playing().withDebugScreenShown()).onlyText().text());
    }

    @Test
    void hiding_while_chat_is_open_is_off_until_the_player_switches_it_on() {
        assertEquals("144 FPS", drawn(HudItem.FPS, playing().withChatOpen()).onlyText().text());

        settings().set(Settings.FPS_READOUT_ITEM.hideWithChat, true);

        assertTrue(drawn(HudItem.FPS, playing().withChatOpen()).drawn().isEmpty());
        assertEquals("144 FPS", drawn(HudItem.FPS, playing()).onlyText().text(), "it stayed hidden after chat closed");
    }

    @Test
    void only_when_relevant_is_on_by_default_for_an_item_that_declares_it() {
        FakeGameView singleplayer = new FakeGameView();
        assertTrue(drawn(HudItem.PING, singleplayer).drawn().isEmpty(), "the ping showed with nothing to show");

        settings().set(Settings.PING_READOUT_ITEM.onlyWhenRelevant, false);

        assertEquals("-- ms", drawn(HudItem.PING, singleplayer).onlyText().text());
    }

    @Test
    void an_item_that_always_has_something_to_show_is_offered_no_only_when_relevant() {
        assertTrue(HudItem.PING.onlyWhenRelevant());
        assertFalse(HudItem.FPS.onlyWhenRelevant());
        assertNull(Settings.FPS_READOUT_ITEM.onlyWhenRelevant);
        assertFalse(Settings.FPS_READOUT_ITEM.all().contains(Settings.PING_READOUT_ITEM.onlyWhenRelevant));
    }

    @Test
    void f1_hides_every_one_of_ash_s_items_whatever_their_options() {
        Settings settings = settings();
        for (HudItem item : HudItem.ALL) {
            settings.set(item.settings().hideWithDebug, false);
            if (item.settings().onlyWhenRelevant != null) {
                settings.set(item.settings().onlyWhenRelevant, false);
            }
        }
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize().withHudHidden();

        new HudItems(new HudLayout(settings), playing(), true, () -> 0L).draw(surface);

        assertTrue(surface.drawn().isEmpty(), "drew " + surface.drawn());
    }

    @Test
    void in_edit_hud_every_item_shows_so_that_it_can_be_placed() {
        HudLayout layout = new HudLayout(settings());
        layout.setEditing(true);
        FakeHudSurface surface = FakeHudSurface.ofTypicalSize();

        new HudItems(layout, new FakeGameView().withChatOpen().withDebugScreenShown(), true, () -> 0L).draw(surface);

        assertEquals(2, surface.drawn().size(), "drew " + surface.drawn());
    }

    @Test
    void a_release_build_hides_under_f3_and_never_for_chat_whatever_the_settings_say() {
        Settings settings = settings();
        settings.set(Settings.FPS_READOUT_ITEM.hideWithDebug, false);
        settings.set(Settings.FPS_READOUT_ITEM.hideWithChat, true);
        HudLayout layout = new HudLayout(settings);

        FakeHudSurface underDebug = FakeHudSurface.ofTypicalSize();
        new HudItems(layout, playing().withDebugScreenShown()).draw(HudItem.FPS, underDebug);
        FakeHudSurface inChat = FakeHudSurface.ofTypicalSize();
        new HudItems(layout, playing().withChatOpen()).draw(HudItem.FPS, inChat);

        assertTrue(underDebug.drawn().isEmpty());
        assertEquals("144 FPS", inChat.onlyText().text());
    }
}
