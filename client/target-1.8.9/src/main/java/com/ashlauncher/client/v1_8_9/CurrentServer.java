package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.freelook.BlockList;
import com.ashlauncher.client.servers.RecentServers;
import com.ashlauncher.client.v1_8_9.mixin.MinecraftClientAccess;
import java.io.IOException;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The server being played on, as 1.8.9 keeps it: the address the player
 * connected with, and the server's own name for itself. See
 * {@code docs/research/0009}, sections 1 and 2.
 */
public final class CurrentServer {

    private static final Logger LOG = LogManager.getLogger("ash");

    private static String logged = "";

    /** The address this connection was last recorded under, so each join is recorded once. */
    private static String joined = null;

    private CurrentServer() {
    }

    /**
     * The address as the player wrote it, or null in singleplayer or out of a
     * world. A list or direct join keeps it in the server entry; a join from
     * launch arguments has none, and its host is the one launch named.
     */
    public static String address() {
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

    /** The server's own name for itself, or null before it has said. */
    public static String brand() {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        return minecraft.player == null ? null : minecraft.player.getServerBrand();
    }

    /** The listed server being played on, if any. */
    public static BlockList.Server blockedHere() {
        return BlockList.match(address());
    }

    /**
     * Once a tick. Says which server this is and what it calls itself, once
     * each time either changes: how a listed server's brand gets established
     * (research 0009, section 2), read from the game's own log.
     */
    public static void tick() {
        String address = address();
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
