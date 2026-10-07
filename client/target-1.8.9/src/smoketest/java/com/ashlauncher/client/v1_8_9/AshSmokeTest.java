package com.ashlauncher.client.v1_8_9;

import java.awt.image.BufferedImage;
import java.io.File;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.freelook.Freelook;
import com.ashlauncher.client.freelook.FreelookHook;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.snaplook.Snaplook;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.ui.Rect;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
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
            // Peaceful, so no slime wanders into the middle of the screen
            // between two frames whose crosshair pixels are compared.
            client.options.difficulty = net.minecraft.world.Difficulty.PEACEFUL;
            client.startIntegratedServer("ash-smoke-test", "ash smoke test",
                    new LevelInfo(0L, LevelInfo.GameMode.SURVIVAL, false, false, LevelGeneratorType.FLAT));
        });
        await("join a world", () ->
                client.world != null && client.player != null && client.currentScreen == null ? client : null);

        pause(IN_WORLD_MS);
        screenshot(client, "ash-in-world.png");

        toggleSprintWorks(client);
        freelookWorks(client);
        snaplookWorks(client);
        pingReadoutIsHiddenInSingleplayer(client);
        settingsScreenWorks(client);
        crosshairOptionsWork(client);
        crosshairWorks(client);
        hitIndicatorWorks(client);
        moveReadoutsWorks(client);
        panelIsCrispAtEveryGuiScale(client);

        System.out.println("ash smoke test: a 1.8.9 client is up, ash is loaded, wrote its settings,"
                + " drew its HUD in a world, toggle sprint started and stopped a sprint, freelook turned the view and drew the terrain behind,"
                + " without turning the player, snaplook showed the front view and put the view back, and ash's settings"
                + " opened on their key and switched the FPS readout off and on, and ash's crosshair drew in place"
                + " of the game's and gave way to it when switched off, and its options changed what it drew, and"
                + " the hit indicator marked the player's own hit on a pig and not a hurt the player had not attacked it for,"
                + " and ash's panel drew the same pixels at every GUI scale");
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
            "{ \"id\": \"crosshair\", \"name\": \"Crosshair\", \"status\": \"loaded\" }",
            "{ \"id\": \"hit-indicator\", \"name\": \"Hit indicator\", \"status\": \"loaded\" }",
            "{ \"id\": \"freelook\", \"name\": \"Freelook\", \"status\": \"loaded\" }",
            "{ \"id\": \"snaplook\", \"name\": \"Snaplook\", \"status\": \"loaded\" }",
            "{ \"id\": \"ping-readout\", \"name\": \"Ping readout\", \"status\": \"loaded\" }",
            "{ \"id\": \"hit-colour\", \"name\": \"Hit colour\", \"status\": \"loaded\" }",
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
        screenshot(client, "ash-settings-panel.png");

        click(client, screen, Feature.FPS_READOUT);
        pause(500L);
        expectFileSays("fps-readout.enabled=false");
        expectReportSays("{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"off\" }");
        expectReadoutDraws(client, false, "the FPS readout still draws after it was switched off");
        // By eye: no frame rate top-left, the marker still bottom-left, the
        // HUD readable through the screen.
        screenshot(client, "ash-settings-fps-readout-off.png");

        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings on the key that opened them", () -> client.currentScreen == null ? client : null);

        tap(client, settingsKey.getCode());
        AshSettingsScreen again = await("open ash's settings a second time", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        click(client, again, Feature.FPS_READOUT);
        pause(500L);
        expectFileSays("fps-readout.enabled=true");
        expectReadoutDraws(client, true, "the FPS readout did not come back when it was switched on");
        keyIntoScreen(client, again, Keyboard.KEY_ESCAPE);
        await("close ash's settings on Escape", () -> client.currentScreen == null ? client : null);
    }

    /**
     * ash's crosshair, in a real world, judged by pixels against a frame with
     * no crosshair in it at all (F1).
     *
     * <p>On: the centre and all four arm tips are exactly ash's white, and
     * the pixel past a tip is exactly its outline. ash's default plus covers
     * the game's own pixel for pixel, so the game's drawn as well would have
     * inverted them.
     *
     * <p>Off, on the settings screen: the game's crosshair is back, which
     * inverts what is behind it - so its centre is the inverse of the same
     * pixel with no crosshair. "Not white" would not do: it is also what no
     * crosshair at all looks like. Switched back on at the end, so a second
     * run in the same directory starts as the first did.
     */
    private static void crosshairWorks(MinecraftClient client) {
        Boolean drew = onClient(client, () -> {
            RecordingSurface surface = new RecordingSurface();
            return CrosshairHook.draw(surface, 0, 0) && surface.fills > 0;
        });
        if (drew == null || !drew) {
            fail("ash's crosshair is on but drew nothing");
        }

        clearMobs(client);
        Frame none = frame(client, "ash-crosshair-none.png", true);
        Frame ash = frame(client, "ash-crosshair.png", false);
        for (int[] at : new int[][] {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            ash.expect(at[0], at[1], 0xFFFFFF, "ash's white crosshair");
        }
        ash.expect(5, 0, 0x000000, "ash's crosshair's outline");

        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);
        switchCrosshair(client, settingsKey, "crosshair.enabled=false");
        Boolean stillDraws = onClient(client, () -> CrosshairHook.draw(new RecordingSurface(), 0, 0));
        if (stillDraws == null || stillDraws) {
            fail("ash's crosshair still draws after it was switched off");
        }

        Frame game = frame(client, "ash-crosshair-off.png", false);
        for (int[] at : new int[][] {{0, 0}, {4, 0}}) {
            game.expectInverseOf(none, at[0], at[1]);
        }

        switchCrosshair(client, settingsKey, "crosshair.enabled=true");
    }

    /** Opens ash's settings, presses one switch, checks the file, and closes them again. */
    private static void switchCrosshair(MinecraftClient client, KeyBinding settingsKey, String fileSays) {
        tap(client, settingsKey.getCode());
        AshSettingsScreen screen = await("open ash's settings for the crosshair", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        click(client, screen, Feature.CROSSHAIR);
        pause(500L);
        expectFileSays(fileSays);
        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings after the crosshair", () -> client.currentScreen == null ? client : null);
    }

    /**
     * Every entity but the player, removed on the server, so nothing walks
     * behind the crosshair between frames whose pixels are compared. Peaceful
     * alone did not do it: a mob on the horizon once crossed an arm tip
     * between the frame without a crosshair and the one with the game's.
     */
    private static void clearMobs(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        onServer(server, () -> {
            for (net.minecraft.entity.Entity entity : new ArrayList<>(server.worlds[0].loadedEntities)) {
                if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                    entity.remove();
                }
            }
            return Boolean.TRUE;
        });
        await("see the mobs go", () -> {
            for (Object entity : onClient(client, () -> new ArrayList<>(client.world.loadedEntities))) {
                if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                    return null;
                }
            }
            return client;
        });
    }

    /**
     * A screenshot, optionally with the HUD hidden, and where the game's own
     * crosshair centre is in it: (width / 2, height / 2) in GUI units, as
     * 1.8.9 draws its crosshair from width / 2 - 7.
     */
    private static Frame frame(MinecraftClient client, String name, boolean hudHidden) {
        onClient(client, () -> {
            client.options.hudHidden = hudHidden;
            return null;
        });
        pause(500L);
        screenshot(client, name);
        onClient(client, () -> {
            client.options.hudHidden = false;
            return null;
        });
        int[] geometry = onClient(client, () -> {
            Window window = new Window(client);
            return new int[] {window.getWidth() / 2, window.getHeight() / 2, window.getScaleFactor()};
        });
        File shot = new File(new File(client.runDirectory, "screenshots"), name);
        try {
            return new Frame(ImageIO.read(shot), geometry[0], geometry[1], geometry[2], name);
        } catch (IOException unreadable) {
            fail("could not read the screenshot " + shot + " (" + unreadable + ")");
            return null;
        }
    }

    /** One screenshot, read by GUI pixel around the crosshair's centre. */
    private static final class Frame {

        final BufferedImage image;
        final int centreX;
        final int centreY;
        final int scale;
        final String name;

        Frame(BufferedImage image, int centreX, int centreY, int scale, String name) {
            this.image = image;
            this.centreX = centreX;
            this.centreY = centreY;
            this.scale = scale;
            this.name = name;
        }

        /** The colour of the middle of the GUI pixel this far from the centre. */
        int at(int dx, int dy) {
            return image.getRGB((centreX + dx) * scale + scale / 2, (centreY + dy) * scale + scale / 2) & 0xFFFFFF;
        }

        void expect(int dx, int dy, int colour, String what) {
            if (at(dx, dy) != colour) {
                fail(name + ": at " + dx + "," + dy + " from the centre, expected " + what + " #"
                        + Integer.toHexString(colour) + " but found #" + Integer.toHexString(at(dx, dy)));
            }
        }

        /** Each channel within a few levels of the inverse: the game's crosshair over the same world. */
        void expectInverseOf(Frame without, int dx, int dy) {
            int found = at(dx, dy);
            int inverse = ~without.at(dx, dy) & 0xFFFFFF;
            for (int shift = 0; shift <= 16; shift += 8) {
                if (Math.abs(((found >> shift) & 0xFF) - ((inverse >> shift) & 0xFF)) > 24) {
                    fail(name + ": at " + dx + "," + dy + " from the centre, expected the game's inverting crosshair, #"
                            + Integer.toHexString(inverse) + ", but found #" + Integer.toHexString(found)
                            + " - with none it is #" + Integer.toHexString(without.at(dx, dy)));
                }
            }
        }
    }

    /** A binding by name, checked against every other binding's default key. */
    /**
     * Freelook, held with its key: the view turns all the way round, the
     * terrain behind the player is drawn, and the player's own rotation - on
     * the client and as the server holds it - never moves.
     *
     * <p>Turned through freelook's own hook rather than the mouse. The game's
     * mouse is read through LWJGL's {@code Mouse}, which nothing here can feed,
     * and it turns only while the window is active, which under Xvfb it never
     * is. That the mouse reaches the hook is the mixin's to show, and its
     * landing is checked at startup and in the load report.
     *
     * <p>The terrain is the point on this target: the check of which chunks are
     * visible reads the player's rotation, so a view turned without it would
     * show the world behind with its chunks missing. So the chunks drawn looking
     * behind are counted against those drawn looking ahead, and the picture is
     * kept for a person to look at.
     */
    private static void freelookWorks(MinecraftClient client) {
        KeyBinding key = binding(client, Freelook.BINDING_NAME);
        int code = key.getCode();
        float[] before = onClient(client, () -> new float[] {client.player.yaw, client.player.pitch});
        int ahead = chunksDrawn(client);

        hold(client, code, true);
        pause(300L);
        if (!onClient(client, () -> FreelookKey.freelook().active())) {
            fail("holding the freelook key did not start freelook");
        }
        if (onClient(client, () -> client.options.perspective) != 1) {
            fail("freelook held in first person did not move the view behind the player");
        }

        // Half a turn, at the game's own 0.15 degrees per unit.
        onClient(client, () -> FreelookHook.turn(180 / 0.15, 0));
        pause(1_500L);
        int behind = chunksDrawn(client);
        screenshot(client, "ash-freelook-behind.png");
        System.out.println("ash smoke test: freelook drew " + behind + " chunks looking behind, " + ahead + " ahead");
        if (behind == 0 || behind * 2 < ahead) {
            fail("looking behind with freelook drew " + behind + " chunks against " + ahead
                    + " ahead: the terrain behind was not brought into view");
        }

        float[] after = onClient(client, () -> new float[] {client.player.yaw, client.player.pitch});
        if (after[0] != before[0] || after[1] != before[1]) {
            fail("freelook turned the player: " + before[0] + "," + before[1] + " became " + after[0] + "," + after[1]);
        }
        IntegratedServer server = client.getServer();
        float seenByServer = onServer(server, () -> server.worlds[0].playerEntities.get(0).yaw);
        if (Math.abs(seenByServer - before[0]) > 0.01) {
            fail("the server was told the camera's turn: it has the player at " + seenByServer + ", not " + before[0]);
        }

        hold(client, code, false);
        pause(300L);
        if (onClient(client, () -> FreelookKey.freelook().active())) {
            fail("letting go of the freelook key did not end freelook");
        }
        if (onClient(client, () -> client.options.perspective) != 0) {
            fail("ending freelook did not put the first-person view back");
        }
    }

    /**
     * Snaplook, held with its key through the same statics a keyboard drives:
     * the game's own front view while it is down, and back to the view the
     * player had - first person, and third person from behind - on letting go.
     */
    private static void snaplookWorks(MinecraftClient client) {
        int code = binding(client, Snaplook.BINDING_NAME).getCode();
        for (int had : new int[] {0, 1}) {
            onClient(client, () -> client.options.perspective = had);
            hold(client, code, true);
            pause(300L);
            if (onClient(client, () -> client.options.perspective) != 2) {
                fail("holding the snaplook key in view " + had + " did not show the front view");
            }
            if (had == 0) {
                screenshot(client, "ash-snaplook.png");
            }
            hold(client, code, false);
            pause(300L);
            int back = onClient(client, () -> client.options.perspective);
            if (back != had) {
                fail("letting go of snaplook left view " + back + ", not the " + had + " it had");
            }
        }
        onClient(client, () -> client.options.perspective = 0);
    }

    /**
     * The ping readout, switched on, in this test's own singleplayer world:
     * hidden, because a latency to one's own world means nothing.
     */
    private static void pingReadoutIsHiddenInSingleplayer(MinecraftClient client) {
        List<String> drawn = onClient(client, () -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.pingReadout.draw(surface);
            return surface.drawn;
        });
        if (!drawn.isEmpty() || onClient(client, AshClient::latency) != null) {
            fail("the ping readout showed in a singleplayer world: " + drawn);
        }
    }

    /** How many chunks the world renderer drew last frame: its own debug line, "C: drawn/total ...". */
    private static int chunksDrawn(MinecraftClient client) {
        String line = onClient(client, () -> client.worldRenderer.getChunksDebugString());
        try {
            return Integer.parseInt(line.substring(line.indexOf("C: ") + 3, line.indexOf('/')));
        } catch (RuntimeException unreadable) {
            fail("the world renderer's chunk line is not as expected: " + line);
            return 0;
        }
    }

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

    /**
     * The crosshair's options page, used as a player would: open it from the
     * card, choose a shape, set a size on the slider, pick a colour, and the
     * file and the crosshair the game draws both follow. Then "Reset to defaults",
     * which the crosshair check after this one relies on.
     */
    private static void crosshairOptionsWork(MinecraftClient client) {
        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);
        tap(client, settingsKey.getCode());
        AshSettingsScreen screen = await("open ash's settings for the crosshair's options", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        pause(300L);

        clickOn(client, screen, "the crosshair's options link", panel -> panel.optionsLinkOf(Feature.CROSSHAIR));
        clickOn(client, screen, "the dot", panel -> panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));
        clickOn(client, screen, "size 6 on its slider", panel -> panel.sliderAt(Settings.CROSSHAIR_SIZE, 6));
        clickOn(client, screen, "the red swatch", panel -> panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));
        // By eye: the page, with a red dot in all three previews.
        screenshot(client, "ash-crosshair-options.png");

        expectFileSays("crosshair.shape=dot");
        expectFileSays("crosshair.size=6");
        expectFileSays("crosshair.colour=#FF4D4DFF");

        // The panel closed, the world's own frame: a red square six wide,
        // from three left of the centre to two right of it, outlined.
        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings to see the red dot", () -> client.currentScreen == null ? client : null);
        Frame dot = frame(client, "ash-crosshair-red-dot.png", false);
        for (int[] at : new int[][] {{0, 0}, {2, 2}, {-3, -3}, {2, -3}}) {
            dot.expect(at[0], at[1], 0xFF4D4D, "the red dot");
        }
        dot.expect(3, 0, 0x000000, "the dot's outline");
        dot.expect(-4, 0, 0x000000, "the dot's outline");

        tap(client, settingsKey.getCode());
        screen = await("reopen ash's settings to reset the crosshair", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        pause(300L);
        clickOn(client, screen, "the crosshair's options link", panel -> panel.optionsLinkOf(Feature.CROSSHAIR));
        clickOn(client, screen, "Reset to defaults", Panel::resetToDefaults);
        expectFileSays("crosshair.shape=cross");
        expectFileSays("crosshair.size=4");
        expectFileSays("crosshair.colour=#FFFFFFFF");
        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings after the crosshair's options", () -> client.currentScreen == null ? client : null);
    }

    /**
     * Waits until ash's panel has stopped moving (#66) - opening, a page
     * changing, tiles arriving - as a player's eye waits for things to land
     * before clicking.
     */
    private static void settle(MinecraftClient client, AshSettingsScreen screen) {
        await("see ash's panel stop moving", () -> onClient(client, () -> screen.panel().animating()) ? null : client);
    }

    /** A left click in the middle of a feature's switch on ash's panel, as the game's input loop delivers one. */
    private static void click(MinecraftClient client, AshSettingsScreen screen, Feature feature) {
        clickOn(client, screen, feature + "'s switch", panel -> panel.switchOf(feature));
    }

    /**
     * A left click in the middle of something on ash's panel, as the game's
     * input loop delivers one - press, then release - where the panel says it
     * drew it. Then a moment for a frame to draw what changed.
     */
    private static void clickOn(MinecraftClient client, AshSettingsScreen screen, String what,
            java.util.function.Function<Panel, Rect> where) {
        settle(client, screen);
        Boolean clicked = onClient(client, () -> {
            Rect target = where.apply(screen.panel());
            if (target == null) {
                return false;
            }
            // Real pixels: the panel is drawn in them, and so is where it says it drew.
            screen.clickAt(target.centreX(), target.centreY());
            screen.mouseReleased(target.centreX(), target.centreY(), 0);
            return true;
        });
        if (clicked == null || !clicked) {
            fail("ash's panel does not show " + what);
        }
        pause(300L);
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

    /**
     * Edit HUD, opened from the panel: the ping readout - which this
     * singleplayer world would otherwise hide, and which Edit HUD shows so it
     * can be moved - taken by the mouse and dragged to the bottom-right,
     * saved there and drawn there; then Reset puts it back and Done returns
     * to the panel.
     */
    private static void moveReadoutsWorks(MinecraftClient client) {
        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);
        tap(client, settingsKey.getCode());
        AshSettingsScreen screen = await("open ash's settings for Edit HUD", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        clickOn(client, screen, "Edit HUD", Panel::editHudButton);

        // The HUD, drawn beneath, says how big each readout is.
        Rect box = await("see the ping readout to move", () ->
                onClient(client, () -> screen.panel().readoutBox(HudLayout.Readout.PING)));
        onClient(client, () -> {
            screen.clickAt(box.centreX(), box.centreY());
            screen.dragAt(client.width * 3 / 5, client.height * 3 / 5);
            screen.dragAt(client.width - 30, client.height - 30);
            return Boolean.TRUE;
        });
        pause(300L);
        // By eye: the box in the bottom-right, "-- ms" in it, its tag saying so.
        screenshot(client, "ash-edit-hud.png");
        onClient(client, () -> {
            screen.mouseReleased(client.width - 30, client.height - 30, 0);
            return Boolean.TRUE;
        });

        expectFileHasLineStarting("ping-readout.position=bottom-right ");
        int[] drawnAt = onClient(client, () -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.pingReadout.draw(surface);
            return new int[] {surface.lastX, surface.lastY};
        });
        if (drawnAt[0] < 427 / 2 || drawnAt[1] < 240 / 2) {
            fail("the ping readout was dropped bottom-right but draws at " + drawnAt[0] + "," + drawnAt[1]
                    + " of 427 by 240");
        }

        clickOn(client, screen, "Edit HUD's Reset", Panel::resetReadoutsButton);
        expectFileSays("ping-readout.position=top-left 4 15");
        clickOn(client, screen, "Edit HUD's Done", Panel::doneButton);
        if (onClient(client, () -> screen.panel().editingHud())) {
            fail("Done did not go back to the panel");
        }
        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings after Edit HUD", () -> client.currentScreen == null ? client : null);
    }

    private static void expectFileHasLineStarting(String start) {
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        try {
            for (String line : Files.readAllLines(settings, StandardCharsets.UTF_8)) {
                if (line.startsWith(start)) {
                    return;
                }
            }
            fail("ash.properties has no line starting " + start + ": "
                    + new String(Files.readAllBytes(settings), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            fail("ash.properties could not be read (" + unreadable + ")");
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

    /**
     * The hit indicator, in this test's own world. Its options are set on the
     * settings screen first - the longest duration, so the mark is still up
     * when it is looked for. Then a pig, spawned on the integrated server.
     * Hurt by something the player did not attack it with, it must not mark.
     * Attacked by the player, it must: the server's hurt, matched to that
     * attack. This tier cannot press the mouse button, so the attack starts at
     * the call the button reaches, {@code attackEntity} - which is also where
     * ash records it.
     */
    private static void hitIndicatorWorks(MinecraftClient client) {
        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);
        tap(client, settingsKey.getCode());
        AshSettingsScreen screen = await("open ash's settings for the hit indicator's options", () ->
                client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
        pause(300L);
        clickOn(client, screen, "the hit indicator's options link", panel -> panel.optionsLinkOf(Feature.HIT_INDICATOR));
        clickOn(client, screen, "1000 ms on its slider", panel -> panel.sliderAt(Settings.HIT_INDICATOR_DURATION, 1000));
        clickOn(client, screen, "the green swatch", panel -> panel.swatchOf(Settings.HIT_INDICATOR_COLOUR, 0x4DFF88));
        // By eye: the page, with Duration at 1000 ms and green chosen.
        screenshot(client, "ash-hit-indicator-options.png");
        expectFileSays("hit-indicator.duration=1000");
        expectFileSays("hit-indicator.colour=#4DFF88FF");
        keyIntoScreen(client, screen, settingsKey.getCode());
        await("close ash's settings after the hit indicator's options", () -> client.currentScreen == null ? client : null);

        IntegratedServer server = client.getServer();
        double[] at = onClient(client, () -> new double[] {client.player.x, client.player.y, client.player.z});
        PigEntity pig = onServer(server, () -> {
            ServerWorld world = server.worlds[0];
            PigEntity spawned = new PigEntity(world);
            spawned.refreshPositionAndAngles(at[0], at[1], at[2] + 2, 0.0F, 0.0F);
            spawned.setAiDisabled(true);
            world.spawnEntity(spawned);
            return spawned;
        });
        int id = pig.getEntityId();
        Entity seen = await("the pig to reach the client", () -> onClient(client, () -> client.world.getEntityById(id)));

        onServer(server, () -> pig.damage(DamageSource.GENERIC, 1.0F));
        // The hurt reaching the client, or "no mark" would hold for a status
        // that never arrived.
        await("the pig's hurt to reach the client", () ->
                onClient(client, () -> ((LivingEntity) seen).hurtTime > 0) ? client : null);
        pause(200L);
        if (hitIndicatorShows(client)) {
            fail("the pig, hurt by nothing the player attacked it with, lit the hit indicator");
        }

        // Past the invulnerability that hurt left it with, so the player's hit is a fresh one.
        pause(1500L);
        onClient(client, () -> {
            client.interactionManager.attackEntity(client.player, seen);
            return null;
        });
        pause(300L);
        if (!hitIndicatorShows(client)) {
            fail("the player's hit on the pig, confirmed by the server, did not light the hit indicator");
        }
        // By eye: four short green diagonals around the crosshair.
        screenshot(client, "ash-hit-indicator.png");
        onServer(server, () -> {
            pig.remove();
            return null;
        });
    }

    /** Whether the hit indicator draws a mark right now, asked of the feature itself. */
    private static boolean hitIndicatorShows(MinecraftClient client) {
        Boolean shows = onClient(client, () -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.hitIndicator.draw(surface, 0, 0);
            return surface.fills > 0;
        });
        return shows != null && shows;
    }

    /**
     * ash's panel draws in the screen's real pixels, so it looks exactly the
     * same at every GUI scale: the white pixels of its SETTINGS letters, and
     * the bright pixels of the Crosshair tile's icon, are the same pixels at
     * each. At this test's 854 by 480 window the game
     * offers scales 1 and 2, and Auto; the 1.21.11 test, which can resize its
     * window, covers 3 as well.
     */
    private static void panelIsCrispAtEveryGuiScale(MinecraftClient client) {
        KeyBinding settingsKey = binding(client, SettingsScreen.BINDING_NAME);
        java.util.Set<Long> first = null;
        java.util.Set<Long> firstIcon = null;
        for (int scale : new int[] {1, 2, 0}) {
            onClient(client, () -> {
                client.options.guiScale = scale;
                return null;
            });
            tap(client, settingsKey.getCode());
            AshSettingsScreen screen = await("open ash's settings at GUI scale " + scale, () ->
                    client.currentScreen instanceof AshSettingsScreen ? (AshSettingsScreen) client.currentScreen : null);
            pause(500L);
            settle(client, screen);
            Rect letters = onClient(client, () -> screen.panel().lettersArea());
            Rect iconArea = onClient(client, () -> screen.panel().tileIconOf(Feature.CROSSHAIR));
            String name = "ash-panel-gui-scale-" + (scale == 0 ? "auto" : scale) + ".png";
            screenshot(client, name);
            keyIntoScreen(client, screen, settingsKey.getCode());
            await("close ash's settings at GUI scale " + scale, () -> client.currentScreen == null ? client : null);
            File shot = new File(new File(client.runDirectory, "screenshots"), name);
            java.util.Set<Long> white = whitePixels(shot, letters, 240);
            java.util.Set<Long> icon = whitePixels(shot, iconArea, 170);
            if (icon.size() < 30) {
                fail("the Crosshair tile has no icon at GUI scale " + scale + ": " + icon.size()
                        + " bright pixels in " + iconArea);
            }
            if (firstIcon == null) {
                firstIcon = icon;
            } else if (!firstIcon.equals(icon)) {
                fail("at GUI scale " + (scale == 0 ? "Auto" : scale)
                        + " the Crosshair icon is not the same pixels as at GUI scale 1");
            }
            if (white.size() < 50) {
                fail("SETTINGS is not on the panel at GUI scale " + scale + ": " + white.size() + " white pixels in "
                        + letters);
            }
            if (first == null) {
                first = white;
            } else if (!first.equals(white)) {
                fail("at GUI scale " + (scale == 0 ? "Auto" : scale) + " SETTINGS is not the same pixels as at GUI scale"
                        + " 1: the panel is not drawn at real resolution");
            }
        }
        onClient(client, () -> {
            client.options.guiScale = 0;
            return null;
        });
    }

    /** Where a screenshot is at least {@code level} in every channel, inside a rectangle of real pixels, as points relative to it. */
    private static java.util.Set<Long> whitePixels(File shot, Rect area, int level) {
        BufferedImage image = null;
        try {
            image = ImageIO.read(shot);
        } catch (IOException unreadable) {
            fail("could not read the screenshot " + shot + " (" + unreadable + ")");
        }
        java.util.Set<Long> white = new java.util.HashSet<>();
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) >= level && ((rgb >> 8) & 0xFF) >= level && (rgb & 0xFF) >= level) {
                    white.add(((long) (x - area.x) << 32) | (y - area.y));
                }
            }
        }
        return white;
    }

    /** Runs {@code work} on the integrated server's thread and waits for its answer. */
    private static <T> T onServer(IntegratedServer server, Callable<T> work) {
        AtomicReference<T> answer = new AtomicReference<>();
        AtomicReference<Exception> thrown = new AtomicReference<>();
        try {
            server.submit(() -> {
                try {
                    answer.set(work.call());
                } catch (Exception failed) {
                    thrown.set(failed);
                }
            }).get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting on the server thread");
        } catch (ExecutionException | TimeoutException failed) {
            fail("the server thread did not answer (" + failed + ")");
        }
        if (thrown.get() != null) {
            fail("on the server thread: " + thrown.get());
        }
        return answer.get();
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
        /** Where the last text drew, in GUI units. */
        int lastX = -1;
        int lastY = -1;
        int fills;

        @Override
        public int width() {
            return 427;
        }

        @Override
        public int textWidth(String text) {
            return text.length() * 6;
        }

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
            lastX = x;
            lastY = y;
        }

        @Override
        public void fill(int x, int y, int width, int height, int colour) {
            fills++;
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
