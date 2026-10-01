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
import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.sprint.ToggleSprint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.input.Keyboard;

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
        settingsScreenWorks(client);

        System.out.println("ash smoke test: a 1.8.9 client is up, ash is loaded, wrote its settings,"
                + " drew its HUD in a world, toggle sprint started and stopped a sprint, and ash's settings"
                + " opened on their key and switched the FPS readout off and on");
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

        // And the load report, which the launcher reads before the next play.
        // Every feature loaded here, or a mixin stopped matching this game
        // version - and the game is still running to say so.
        Path report = FabricLoader.getInstance().getGameDir().resolve(LoadReport.RELATIVE_PATH);
        String reported;
        try {
            reported = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            fail("the vanilla client started without ash writing its load report (" + missing + ")");
            return;
        }
        System.out.println("ash smoke test: load report " + reported.replace('\n', ' '));
        for (String feature : new String[] {
            "{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"loaded\" }",
            "{ \"id\": \"toggle-sprint\", \"name\": \"Toggle sprint\", \"status\": \"loaded\" }",
            "{ \"id\": \"settings-screen\", \"name\": \"ash's settings screen\", \"status\": \"loaded\" }",
        }) {
            expectReportSays(feature);
        }
    }

    /**
     * Toggle sprint, with the real mixin and the real latch, driven the way a
     * keyboard drives it: through {@code KeyBinding}'s own statics, on the
     * client thread, as a key event would arrive. There is no input framework
     * on this target, so this is as close to a keyboard as it gets.
     */
    private static void toggleSprintWorks(MinecraftClient client) {
        KeyBinding toggle = binding(client, ToggleSprint.BINDING_NAME);

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

    /**
     * ash's settings screen: opened by its key through the same statics a
     * keyboard drives, then clicked and keyed the way the game's input loop
     * delivers a click and a key to an open screen. Switching the FPS readout
     * off reaches the file and the load report; the same key, then Escape,
     * closes it.
     */
    private static void settingsScreenWorks(MinecraftClient client) {
        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);

        tap(client, settingsKey.getCode());
        AshSettingsScreen screen = await("open ash's settings on their key", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        pause(500L);
        screenshot(client, "ash-settings.png");

        click(client, screen, "FPS readout: On");
        pause(500L);
        expectFileSays("fps-readout.enabled=false");
        expectReportSays("{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"off\" }");
        expectReadoutDraws(client, false, "the FPS readout still draws after it was switched off");
        if (onClient(client, () -> screen.buttonLabelled("FPS readout: Off")) == null) {
            fail("the FPS readout's switch did not change its label");
        }
        // By eye: no frame rate top-left, the marker still bottom-left, the
        // HUD readable through the screen.
        screenshot(client, "ash-settings-fps-readout-off.png");

        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings on the key that opened them", () -> client.currentScreen == null ? client : null);

        tap(client, settingsKey.getCode());
        AshSettingsScreen again = await("open ash's settings a second time", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        click(client, again, "FPS readout: Off");
        pause(500L);
        expectFileSays("fps-readout.enabled=true");
        expectReadoutDraws(client, true, "the FPS readout did not come back when it was switched on");
        keyIntoScreen(client, again, Keyboard.KEY_ESCAPE);
        await("close ash's settings on Escape", () -> client.currentScreen == null ? client : null);
    }

    /** A binding by name, checked against every other binding's default key. */
    private static KeyBinding binding(MinecraftClient client, String name) {
        KeyBinding found = onClient(client, () -> {
            for (KeyBinding binding : client.options.allKeys) {
                if (binding.getTranslationKey().equals(name)) {
                    return binding;
                }
            }
            return null;
        });
        if (found == null) {
            fail("there is no \"" + name + "\" binding in Controls");
            return null;
        }
        // Against every binding the game has, rather than against a list
        // someone wrote down.
        String sharedWith = onClient(client, () -> {
            for (KeyBinding other : client.options.allKeys) {
                if (other != found && other.getDefaultCode() == found.getDefaultCode()) {
                    return other.getTranslationKey();
                }
            }
            return null;
        });
        if (sharedWith != null) {
            fail(name + "'s default key is also " + sharedWith + "'s");
        }
        return found;
    }

    /** A left click in the middle of the button with this label, as the game's input loop delivers one. */
    private static void click(MinecraftClient client, AshSettingsScreen screen, String label) {
        Boolean clicked = onClient(client, () -> {
            ButtonWidget button = screen.buttonLabelled(label);
            if (button == null) {
                return false;
            }
            screen.mouseClicked(button.x + button.getWidth() / 2, button.y + 10, 0);
            return true;
        });
        if (clicked == null || !clicked) {
            fail("there is no \"" + label + "\" button on ash's settings screen");
        }
    }

    /**
     * A key delivered to the open screen as the game's input loop delivers
     * one. This tier cannot press a real key: it proves the screen closes on
     * its key and on Escape, not that the game routes the key to it - which
     * the 1.21.11 test, pressing real keys, does prove.
     */
    private static void keyIntoScreen(MinecraftClient client, AshSettingsScreen screen, int keyCode) {
        onClient(client, () -> {
            screen.keyPressed((char) 0, keyCode);
            return null;
        });
    }

    private static void expectReadoutDraws(MinecraftClient client, boolean expected, String otherwise) {
        List<String> drawn = onClient(client, () -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.fpsReadout.draw(surface);
            return surface.drawn;
        });
        if (drawn == null || drawn.isEmpty() == expected) {
            fail(otherwise + " (drew " + drawn + ")");
        }
    }

    private static void expectFileSays(String line) {
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        try {
            String written = new String(Files.readAllBytes(settings), StandardCharsets.UTF_8);
            if (!written.contains("\n" + line + "\n")) {
                fail("ash.properties does not say " + line + ": " + written);
            }
        } catch (IOException unreadable) {
            fail("ash.properties could not be read (" + unreadable + ")");
        }
    }

    private static void expectReportSays(String entry) {
        Path report = FabricLoader.getInstance().getGameDir().resolve(LoadReport.RELATIVE_PATH);
        try {
            String written = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
            if (!written.contains(entry)) {
                fail("the load report does not say " + entry + ": " + written);
            }
        } catch (IOException unreadable) {
            fail("the load report could not be read (" + unreadable + ")");
        }
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

    /**
     * Records what a feature draws, with nothing hidden: the HUD shown, no
     * debug screen. So whether ash's own FPS readout draws onto it turns on
     * its setting alone - the feature itself, asked in the running game.
     */
    private static final class RecordingSurface implements HudSurface {

        final List<String> drawn = new ArrayList<>();

        @Override
        public int height() {
            return 240;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public void drawText(String text, int x, int y, int colour) {
            drawn.add(text);
        }

        @Override
        public boolean debugScreenShown() {
            return false;
        }

        @Override
        public boolean hudHidden() {
            return false;
        }
    }
}
