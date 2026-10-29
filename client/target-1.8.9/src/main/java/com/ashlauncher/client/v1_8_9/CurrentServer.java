package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.servers.RecentServers;
import java.io.IOException;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The server being played on: each join recorded for the launcher, and what
 * the server calls itself, as 1.8.9 keeps it. The address comes from the game
 * view. See {@code docs/research/0009}, sections 1 and 2.
 */
public final class CurrentServer {

    private static final Logger LOG = LogManager.getLogger("ash");

    private static String logged = "";

    /** The address this connection was last recorded under, so each join is recorded once. */
    private static String joined = null;

    private CurrentServer() {
    }

    /** The server's own name for itself, or null before it has said. */
    public static String brand() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        return minecraft.player == null ? null : minecraft.player.getServerBrand();
    }

    /**
     * Once a tick. Says which server this is and what it calls itself, once
     * each time either changes: how a listed server's brand gets established
     * (research 0009, section 2), read from the game's own log.
     */
    public static void tick() {
        String address = LegacyGameView.INSTANCE.serverAddress();
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
                LOG.warn("ash: could not record this server for the launcher's recent servers: " + unwritable);
            }
        }
        String line = "ash: playing on " + address + ", server brand " + Objects.toString(brand(), "not sent yet");
        if (!line.equals(logged)) {
            logged = line;
            LOG.info(line);
        }
    }
}
