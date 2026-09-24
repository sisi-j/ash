package com.ashlauncher.client.v1_21_11;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

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

        // A CI runner has no GPU, and llvmpipe - the software renderer that
        // stands in for one - draws the loading screen uncapped on every core.
        // The first attempt at a world here sat at "Preparing spawn area: 16%"
        // for a minute beside "Can't keep up! Is the server overloaded?" and
        // timed out: the integrated server never got the CPU. So the client
        // is capped before the world starts, and told not to pause itself for
        // a window that, under Xvfb, never has focus.
        context.runOnClient(client -> {
            client.options.framerateLimit().set(30);
            client.options.pauseOnLostFocus = false;
        });

        // In a world, where the HUD is. Both ash elements should be in this
        // picture: the frame rate top-left and the marker bottom-left.
        Thread watchdog = stackDumpAfter(45_000L);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            watchdog.interrupt();
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("ash-in-world");
        }
    }

    /**
     * DIAGNOSTIC, to be removed: if the world has not loaded by then, print
     * what the server, its world-generation workers and the render thread are
     * doing. The first run of this step stalled at "Preparing spawn area: 16%"
     * for a minute with nothing in the log to say why.
     */
    private static Thread stackDumpAfter(long millis) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException loaded) {
                return;
            }
            System.out.println("ash diagnostic: world not loaded after " + millis / 1000L + "s, thread stacks follow");
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                String name = thread.getName();
                if (name.startsWith("Server thread") || name.startsWith("Worker-Main")
                        || name.startsWith("Render thread") || name.startsWith("Test thread")) {
                    System.out.println("ash diagnostic: \"" + name + "\" " + thread.getState());
                    for (int i = 0; i < Math.min(stack.length, 30); i++) {
                        System.out.println("ash diagnostic:     at " + stack[i]);
                    }
                }
            });
        }, "ash-world-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        return watchdog;
    }
}
