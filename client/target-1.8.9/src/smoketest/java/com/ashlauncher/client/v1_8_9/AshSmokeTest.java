package com.ashlauncher.client.v1_8_9;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * ash's own miniature of Fabric's client game tests, for the target that has
 * none.
 *
 * <p>Legacy Fabric API ships no gametest module at all — none of its 44
 * modules is one — so the tier `target-1.21.11` gets from
 * `fabric-client-gametest-api-v1` does not exist here. What does exist is a
 * real vanilla client that Loom can launch headless. This turns that into a
 * test: wait for the title screen, say whether ash is in it, walk into a flat
 * world, let the HUD draw, keep a picture, and shut the game down so the run
 * has an exit code.
 *
 * <p>The world is the point. ash's HUD is drawn only in one, and the player -
 * whose movement tick is where toggle sprint's mixin lands - exists only in
 * one. A test that stopped at the title screen would pass with every line of
 * ash's in-game code broken, which is what this one did until the FPS readout
 * made that visible.
 *
 * <p>It still proves less than the modern tier and is meant to. There is no
 * way to drive input and no assertion on what was drawn - the screenshot is
 * for a human, and what this proves by itself is "a real 1.8.9 vanilla client,
 * with ash installed, drew ash's HUD in a world for three seconds without
 * crashing".
 *
 * <p>Its manifest names no dependency on `ash`, which looks like an omission
 * and is not. With `depends` on `ash`, the loader would refuse to start when
 * ash was missing and this assertion would never run - the loader would be
 * doing the catching and the test would be along for the ride. Without it, the
 * game starts either way and the line below is what decides, which is the only
 * arrangement in which it means anything.
 *
 * <p>Never shipped: this lives in its own source set and its own mod, and
 * nothing in `src/main` knows it exists.
 */
public final class AshSmokeTest implements ClientModInitializer {

    /** Per step, and generous. A cold CI runner starting a game is not quick. */
    private static final long STEP_TIMEOUT_MS = 180_000L;

    private static final long POLL_MS = 250L;

    /** Long enough for the HUD to have drawn many frames, and the frame counter to tick over. */
    private static final long IN_WORLD_MS = 3_000L;

    @Override
    public void onInitializeClient() {
        // A daemon thread, because this has to watch the vanilla client rather
        // than block the thread that is starting it.
        Thread watcher = new Thread(AshSmokeTest::run, "ash-smoke-test");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void run() {
        // A screen means the game is past its loading and drawing something -
        // the title screen, on a vanilla client with no world.
        MinecraftClient client = await("put its title screen up", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            return c != null && c.currentScreen != null ? c : null;
        });

        checkAshLoaded();

        // Flat, because generation is the slow part of starting a world and a
        // software-GL runner is slow enough already. On the client thread,
        // because that is the only thread the game will start a world from.
        //
        // Not paused on lost focus first. Under Xvfb the window never has
        // focus, so the game opens its pause menu the moment the world is up -
        // the first run of this waited three minutes for a screen that was
        // never going to close - and a paused singleplayer world does not
        // tick the player at all.
        client.submit(() -> {
            client.options.pauseOnLostFocus = false;
            client.startIntegratedServer("ash-smoke-test", "ash smoke test",
                    new LevelInfo(0L, LevelInfo.GameMode.SURVIVAL, false, false, LevelGeneratorType.FLAT));
        });
        await("join a world", () ->
                client.world != null && client.player != null && client.currentScreen == null ? client : null);

        pause(IN_WORLD_MS);
        screenshot(client, "ash-in-world.png");

        toggleSprintWorks(client);

        System.out.println("ash smoke test: a 1.8.9 client is up, ash is loaded, wrote its settings,"
                + " drew its HUD in a world, and toggle sprint started and stopped a sprint");
        // The clean way out: this asks the game to stop, so the run task exits
        // zero and Gradle reports a pass.
        client.scheduleStop();
    }

    private static void checkAshLoaded() {
        // Printed rather than logged, and that is not a style choice. On this
        // target Log4j rejects most of Fabric's logging config on startup -
        // `Error processing element Queue: CLASS_NOT_FOUND`, then every
        // appender reference left with an invalid level - so INFO goes
        // nowhere and the log file CI uploads holds three ERROR lines and
        // nothing else. Standard out is the only record this tier has, so the
        // evidence goes there: the same mod list the modern target gets from
        // the loader's own log.
        System.out.println("ash smoke test: " + describeMods());

        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            fail("the vanilla client started without ash in it, which is the one thing this is for");
        }

        // Written while the client initialised, so a real game directory has
        // one by the time a screen is up. Without it the adapter never loaded
        // settings, and every feature is on defaults nobody can change.
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        String written;
        try {
            written = new String(Files.readAllBytes(settings), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            fail("the vanilla client started without ash writing ash.properties (" + missing + ")");
            return;
        }
        if (!written.contains("fps-readout.enabled=")) {
            fail("ash.properties has no FPS readout setting: " + written);
        }
    }

    /**
     * Toggle sprint, with the real mixin and the real latch, driven the way a
     * keyboard drives it: through {@code KeyBinding}'s own statics, on the
     * client thread, as a key event would arrive. There is no input framework
     * on this target, so this is as close to a keyboard as it gets.
     */
    private static void toggleSprintWorks(MinecraftClient client) {
        KeyBinding toggle = onClient(client, () -> {
            for (KeyBinding binding : client.options.allKeys) {
                if (binding.getTranslationKey().equals("Toggle Sprint")) {
                    return binding;
                }
            }
            return null;
        });
        if (toggle == null) {
            fail("there is no \"Toggle Sprint\" binding in Controls");
            return;
        }
        // Against every binding the game has, rather than against a list
        // someone wrote down.
        String sharedWith = onClient(client, () -> {
            for (KeyBinding other : client.options.allKeys) {
                if (other != toggle && other.getDefaultCode() == toggle.getDefaultCode()) {
                    return other.getTranslationKey();
                }
            }
            return null;
        });
        if (sharedWith != null) {
            fail("toggle sprint's default key is also " + sharedWith + "'s");
        }

        int forward = client.options.forwardKey.getCode();
        int toggleKey = toggle.getCode();

        hold(client, forward, true);
        pause(1_000L);
        expectSprinting(client, false, "holding forward alone started a sprint");

        tap(client, toggleKey);
        pause(1_000L);
        expectSprinting(client, true, "a press of the toggle key did not start a sprint");

        hold(client, forward, false);
        pause(500L);
        tap(client, toggleKey);
        hold(client, forward, true);
        pause(1_000L);
        expectSprinting(client, false, "a second press of the toggle key did not turn it off");
        hold(client, forward, false);
    }

    private static void hold(MinecraftClient client, int key, boolean down) {
        onClient(client, () -> {
            KeyBinding.setKeyPressed(key, down);
            return null;
        });
    }

    /** Down, counted as a press, and up again two ticks later - a real tap. */
    private static void tap(MinecraftClient client, int key) {
        onClient(client, () -> {
            KeyBinding.setKeyPressed(key, true);
            KeyBinding.onKeyPressed(key);
            return null;
        });
        pause(100L);
        hold(client, key, false);
    }

    private static void expectSprinting(MinecraftClient client, boolean expected, String otherwise) {
        Boolean sprinting = onClient(client, () -> client.player.isSprinting());
        if (sprinting == null || sprinting != expected) {
            fail(otherwise + " (sprinting: " + sprinting + ")");
        }
    }

    /** Runs {@code work} on the client thread and waits for its answer. */
    private static <T> T onClient(MinecraftClient client, Callable<T> work) {
        try {
            return client.execute(work).get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting on the client thread");
        } catch (ExecutionException | TimeoutException failed) {
            fail("the client thread did not answer (" + failed + ")");
        }
        return null;
    }

    /**
     * Saves what is on screen, on the render thread because that is where the
     * framebuffer is. Into the run directory's {@code screenshots}, which CI
     * keeps.
     */
    private static void screenshot(MinecraftClient client, String name) {
        try {
            client.submit(() -> ScreenshotUtils.saveScreenshot(
                    client.runDirectory, name, client.width, client.height, client.getFramebuffer()))
                    .get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while taking a screenshot");
        } catch (ExecutionException | TimeoutException failed) {
            fail("could not take a screenshot in the world (" + failed + ")");
        }
    }

    /**
     * Polls until {@code check} answers something, or fails the run.
     *
     * <p>Says what it can see every ten seconds while it waits. A wait that
     * times out in silence is a failure with no diagnosis - which is exactly
     * what the first run of the world step produced.
     */
    private static <T> T await(String what, Check<T> check) {
        long start = System.currentTimeMillis();
        long deadline = start + STEP_TIMEOUT_MS;
        long nextReport = start + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            T answer = check.answer();
            if (answer != null) {
                return answer;
            }
            if (System.currentTimeMillis() >= nextReport) {
                System.out.println("ash smoke test: still waiting to " + what + " after "
                        + (System.currentTimeMillis() - start) / 1000L + "s - " + describeState());
                nextReport += 10_000L;
            }
            pause(POLL_MS);
        }
        fail("the vanilla client did not " + what + " within " + (STEP_TIMEOUT_MS / 1000L) + " seconds");
        return null;
    }

    /** One poll of the game's state; {@code null} means not yet. */
    private interface Check<T> {
        T answer();
    }

    /** What the client is showing and holding, for a wait that is taking too long. */
    private static String describeState() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return "no client yet";
        }
        return "screen " + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName())
                + ", world " + (client.world != null) + ", player " + (client.player != null)
                + ", server " + client.isIntegratedServerRunning();
    }

    /** The loaded mods, by id and version, in a line CI can be grepped for. */
    private static String describeMods() {
        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();
        List<String> names = new ArrayList<>(mods.size());
        for (ModContainer mod : mods) {
            names.add(mod.getMetadata().getId() + " " + mod.getMetadata().getVersion());
        }
        Collections.sort(names);
        return mods.size() + " mods loaded: " + String.join(", ", names);
    }

    private static void fail(String why) {
        System.err.println("ash smoke test failed: " + why);
        // `halt` rather than `exit`: the exit code is the only thing CI reads,
        // and a shutdown hook on a game that is already wrong is a good way to
        // hang instead of failing.
        Runtime.getRuntime().halt(1);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the vanilla client");
        }
    }
}
