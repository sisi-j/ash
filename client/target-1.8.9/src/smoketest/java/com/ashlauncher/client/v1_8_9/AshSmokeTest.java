package com.ashlauncher.client.v1_8_9;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * ash's own miniature of Fabric's client game tests, for the target that has
 * none.
 *
 * <p>Legacy Fabric API ships no gametest module at all — none of its 44
 * modules is one — so the tier `target-1.21.11` gets from
 * `fabric-client-gametest-api-v1` does not exist here. What does exist is a
 * real client that Loom can launch headless. This is the smallest thing that
 * turns that into a test: wait for the game to put a screen up, say whether
 * ash is in it, and shut the game down so the run has an exit code.
 *
 * <p>It proves less than the modern tier and is meant to. There is no ticking,
 * no world, no screenshot and no way to drive input — only "a real 1.8.9
 * client, with ash installed, reached its menu instead of crashing". That is
 * the failure worth catching first, and it is the one that will start
 * happening when #24 puts a mixin on this target.
 *
 * <p>Never shipped: this lives in its own source set and its own mod, and
 * nothing in `src/main` knows it exists.
 */
public final class AshSmokeTest implements ClientModInitializer {

    /** Generous. A cold CI runner starting a JVM and a game is not quick. */
    private static final long TIMEOUT_MS = 180_000L;

    private static final long POLL_MS = 250L;

    @Override
    public void onInitializeClient() {
        // A daemon thread, because this has to watch the client rather than
        // block the thread that is starting it.
        Thread watcher = new Thread(AshSmokeTest::watch, "ash-smoke-test");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void watch() {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            MinecraftClient client = MinecraftClient.getInstance();
            // A screen means the game is past its loading and drawing
            // something - the title screen, on a client with no world.
            if (client != null && client.currentScreen != null) {
                report(client);
                return;
            }
            sleep();
        }

        fail("the client never put a screen up within " + (TIMEOUT_MS / 1000L) + " seconds");
    }

    private static void report(MinecraftClient client) {
        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            fail("the client started without ash in it, which is the one thing this is for");
            return;
        }

        System.out.println("ash smoke test: a 1.8.9 client is up and ash is loaded");
        // The clean way out: this asks the game to stop, so the run task exits
        // zero and Gradle reports a pass.
        client.scheduleStop();
    }

    private static void fail(String why) {
        System.err.println("ash smoke test failed: " + why);
        // `halt` rather than `exit`: the exit code is the only thing CI reads,
        // and a shutdown hook on a game that is already wrong is a good way to
        // hang instead of failing.
        Runtime.getRuntime().halt(1);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the client");
        }
    }
}
