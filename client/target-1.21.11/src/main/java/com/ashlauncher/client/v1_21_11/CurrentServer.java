package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.freelook.BlockList;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The server being played on, as 1.21.11 keeps it: the address the player
 * connected with, and the server's own name for itself. See
 * {@code docs/research/0009}, sections 1 and 2.
 */
final class CurrentServer {

    private final Consumer<String> log;
    private String logged = "";

    CurrentServer(Consumer<String> log) {
        this.log = log;
    }

    /** The address as the player wrote it, or null in singleplayer or out of a world. */
    static String address() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() || minecraft.getConnection() == null) {
            return null;
        }
        ServerData server = minecraft.getCurrentServer();
        return server == null ? null : server.ip;
    }

    /** The server's own name for itself, or null before it has said. */
    static String brand() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.serverBrand();
    }

    /** The listed server being played on, if any. */
    static BlockList.Server blockedHere() {
        return BlockList.match(address());
    }

    /**
     * Once a tick. Says which server this is and what it calls itself, once
     * each time either changes: how a listed server's brand gets established
     * (research 0009, section 2), read from the game's own log.
     */
    void tick() {
        String address = address();
        if (address == null) {
            logged = "";
            return;
        }
        String line = "ash: playing on " + address + ", server brand " + Objects.toString(brand(), "not sent yet");
        if (!line.equals(logged)) {
            logged = line;
            log.accept(line);
        }
    }
}
