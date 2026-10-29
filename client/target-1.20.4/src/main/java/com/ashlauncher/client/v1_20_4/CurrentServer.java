package com.ashlauncher.client.v1_20_4;

import com.ashlauncher.client.servers.RecentServers;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The server being played on: each join recorded for the launcher, and what
 * the server calls itself, as 1.20.4 keeps it. The address comes from the
 * game view. See {@code docs/research/0009}, sections 1 and 2.
 */
final class CurrentServer {

    private final Consumer<String> log;
    private String logged = "";

    /** The address this connection was last recorded under, so each join is recorded once. */
    private String joined = null;

    CurrentServer(Consumer<String> log) {
        this.log = log;
    }

    /** The server's own name for itself, or null before it has said. */
    static String brand() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.serverBrand();
    }

    /**
     * Once a tick. Says which server this is and what it calls itself, once
     * each time either changes: how a listed server's brand gets established
     * (research 0009, section 2), read from the game's own log.
     */
    void tick() {
        String address = MinecraftGameView.INSTANCE.serverAddress();
        if (address == null) {
            logged = "";
            joined = null;
            return;
        }
        if (!address.equals(joined)) {
            joined = address;
            try {
                RecentServers.record(FabricLoader.getInstance().getGameDir(), address, System.currentTimeMillis());
            } catch (IOException unwritable) {
                log.accept("ash: could not record this server for the launcher's recent servers: " + unwritable);
            }
        }
        String line = "ash: playing on " + address + ", server brand " + Objects.toString(brand(), "not sent yet");
        if (!line.equals(logged)) {
            logged = line;
            log.accept(line);
        }
    }
}
