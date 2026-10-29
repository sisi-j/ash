package com.ashlauncher.client.v1_20_4;

import com.ashlauncher.client.game.GameView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 1.20.4's answer to {@link GameView}. Each fact is a line over the game's
 * own state, read when it's asked, so one instance serves everything.
 */
public final class MinecraftGameView implements GameView {

    public static final MinecraftGameView INSTANCE = new MinecraftGameView();

    private MinecraftGameView() {
    }

    @Override
    public boolean debugScreenShown() {
        // The F3 screen itself. On 1.20.4 this is only F3: debug entries
        // that stay on, like 1.21.11's, came later.
        return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
    }

    @Override
    public int framesPerSecond() {
        return Minecraft.getInstance().getFps();
    }

    /**
     * The server's latency for the player's own tab-list entry, the number
     * behind the tab list's bars; null while this client hosts the world -
     * asked as "has a singleplayer server", which stays true after opening to
     * LAN where "is singleplayer" does not (research 0004, 3.1) - or before
     * the server has listed the player. Read, never measured.
     */
    @Override
    public Integer latency() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() || minecraft.player == null || minecraft.getConnection() == null) {
            return null;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? null : info.getLatency();
    }

    @Override
    public boolean chatOpen() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    @Override
    public String serverAddress() {
        // See docs/research/0009, sections 1 and 2.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() || minecraft.getConnection() == null) {
            return null;
        }
        ServerData server = minecraft.getCurrentServer();
        return server == null ? null : server.ip;
    }
}
