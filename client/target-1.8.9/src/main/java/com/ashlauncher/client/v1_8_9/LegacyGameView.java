package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.v1_8_9.mixin.MinecraftClientAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ServerInfo;

/**
 * 1.8.9's answer to {@link GameView}. Each fact is a line over the game's own
 * state, read when it's asked, so one instance serves everything.
 */
public final class LegacyGameView implements GameView {

    public static final LegacyGameView INSTANCE = new LegacyGameView();

    private LegacyGameView() {
    }

    @Override
    public boolean debugScreenShown() {
        return MinecraftClient.getInstance().options.debugEnabled;
    }

    @Override
    public boolean chatOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
    }

    /**
     * A list or direct join keeps the address in the server entry; a join from
     * launch arguments has none, and its host is the one launch named. See
     * {@code docs/research/0009}, sections 1 and 2.
     */
    @Override
    public String serverAddress() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.isInSingleplayer() || minecraft.getNetworkHandler() == null) {
            return null;
        }
        ServerInfo entry = minecraft.getCurrentServerEntry();
        if (entry != null) {
            return entry.address;
        }
        return ((MinecraftClientAccess) minecraft).ash$serverAddress();
    }
}
