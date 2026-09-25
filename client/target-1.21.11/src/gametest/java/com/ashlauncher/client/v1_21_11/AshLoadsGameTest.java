package com.ashlauncher.client.v1_21_11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/**
 * A real vanilla client, launched, with ash in it.
 *
 * <p>Every other tier stops short of this one. The unit tests never start the
 * game; the manifest tests only read what the build wrote. This is the tier
 * that would catch a mixin which has stopped matching its target, and
 * ADR-0017's choice to let a feature degrade rather than crash is safe only
 * because it exists — a degradation nothing detects is indistinguishable from
 * a feature that was never there.
 *
 * <p>Its manifest names no dependency on `ash`, which looks like an omission
 * and is not. With `depends` on `ash`, the loader would refuse to start when
 * ash was missing and this assertion would never run - the loader would be
 * doing the catching and the test would be along for the ride. Without it, the
 * game starts either way and the line below is what decides, which is the only
 * arrangement in which it means anything.
 *
 * <p>What is drawn is asserted by eye rather than by pixel. The screenshot
 * taken in a world is the only automated picture of the HUD there is - the
 * title screen has none - and it is kept as a CI artifact so the readout and
 * the marker can be seen without anyone launching the game.
 */
public class AshLoadsGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // Far enough in that the vanilla client is ticking rather than merely
        // started.
        context.waitTicks(20);

        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            throw new AssertionError(
                    "the vanilla client started without ash in it, which is the one thing this tier is for");
        }

        // The settings file is written while the client initialises, so by now
        // a real game directory has one. Its absence would mean the adapter
        // never loaded settings at all, and every feature was running on
        // defaults nobody could change.
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        try {
            String written = Files.readString(settings);
            if (!written.contains("fps-readout.enabled=")) {
                throw new AssertionError("ash.properties has no FPS readout setting:\n" + written);
            }
        } catch (IOException missing) {
            throw new AssertionError("the client started without writing ash.properties", missing);
        }

        context.takeScreenshot("ash-loaded");

        // Under Xvfb the window never has focus, and a client that pauses
        // itself for that is not a client in a world.
        context.runOnClient(client -> client.options.pauseOnLostFocus = false);

        // In a world, where the HUD is. Both ash elements should be in this
        // picture: the frame rate top-left and the marker bottom-left.
        //
        // A dedicated server rather than a singleplayer world, and not by
        // taste. A singleplayer world never finished loading here: it sat at
        // "Preparing spawn area: 16%" until the framework's minute ran out.
        // The chain, read from 1.21.11's bytecode: a joining player's spawn is
        // prepared while they are not yet in the player list; an integrated
        // server with an empty player list pauses and stops ticking its
        // levels; a paused server only advances chunks in idle time, and only
        // if it is ahead of schedule (`MinecraftServer.pollTaskInternal`
        // checks `haveTime()`); and under the framework's lockstep ticking it
        // was 40 ticks behind. A dedicated server does not pause when empty,
        // so its levels tick and the chunks load whatever the schedule.
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
                TestServerConnection connection = server.connect()) {
            connection.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("ash-in-world");

            toggleSprintWorks(context, server);
        }
    }

    /**
     * Toggle sprint, driven through the same input a player's keyboard feeds,
     * with the real mixin, the real latch and the real server.
     *
     * <p>The server's view is checked as well as the client's because it is
     * the one that matters for the feature's promise: the server can only
     * know the player is sprinting from the ordinary sprint packet a held key
     * sends, so seeing it there is seeing that nothing else was sent.
     */
    private static void toggleSprintWorks(ClientGameTestContext context, TestDedicatedServerContext server) {
        KeyMapping toggle = context.computeOnClient(client -> {
            KeyMapping found = null;
            for (KeyMapping mapping : client.options.keyMappings) {
                if (mapping.getName().equals(AshClient.TOGGLE_SPRINT_BINDING)) {
                    found = mapping;
                }
            }
            if (found == null) {
                throw new AssertionError("no \"" + AshClient.TOGGLE_SPRINT_BINDING + "\" binding in Controls");
            }
            // Against every binding the game has, the F3 combinations
            // included, rather than against a list someone wrote down.
            for (KeyMapping other : client.options.keyMappings) {
                if (other != found && other.getDefaultKey().equals(found.getDefaultKey())) {
                    throw new AssertionError("toggle sprint's default key is also " + other.getName() + "'s");
                }
            }
            return found;
        });

        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        assertSprinting(context, server, false, "holding forward alone started a sprint");

        context.getInput().pressKey(toggle);
        context.waitTicks(20);
        assertSprinting(context, server, true, "a press of the toggle key did not start a sprint");

        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);
        context.getInput().pressKey(toggle);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        assertSprinting(context, server, false, "a second press of the toggle key did not turn it off");
        context.getInput().releaseKey(options -> options.keyUp);
    }

    private static void assertSprinting(
            ClientGameTestContext context, TestDedicatedServerContext server, boolean expected, String otherwise) {
        boolean client = context.computeOnClient(c -> c.player.isSprinting());
        boolean seenByServer = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).isSprinting());
        if (client != expected || seenByServer != expected) {
            throw new AssertionError(otherwise + " (client sprinting: " + client + ", server sees: " + seenByServer + ")");
        }
    }
}
