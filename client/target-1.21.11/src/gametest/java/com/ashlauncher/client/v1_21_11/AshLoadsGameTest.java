package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.freelook.Freelook;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.servers.RecentServers;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.snaplook.Snaplook;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.ui.Rect;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.phys.EntityHitResult;

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

    /** Whether this is the run with Sodium in the mods folder: `runClientGameTestSodium` sets it. */
    private static final boolean WITH_SODIUM = Boolean.getBoolean("ash.gametest.sodium");

    @Override
    public void runTest(ClientGameTestContext context) {
        // Far enough in that the vanilla client is ticking rather than merely
        // started.
        context.waitTicks(20);

        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            throw new AssertionError(
                    "the vanilla client started without ash in it, which is the one thing this tier is for");
        }
        // The same test runs twice in CI: as it is, and with Sodium in the
        // mods folder as a player would add it (#48). Each run insists on
        // the Sodium it was promised: a second run that quietly ran without
        // it would prove nothing, and the first must not have picked it up.
        if (FabricLoader.getInstance().isModLoaded("sodium") != WITH_SODIUM) {
            throw new AssertionError(WITH_SODIUM ? "the run with Sodium started without Sodium in it"
                    : "Sodium is loaded in the run without it");
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

        // And the load report, which the launcher reads before the next play.
        // In a build where every mixin lands, every feature reports loaded -
        // and a mixin that stopped matching this game version fails here, by
        // feature, with the game still running rather than crashed.
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
            assertReportSays(feature);
        }
        // Read from the real loader's mod origins: nothing of a player's is in
        // this game's mods folder - except Sodium, in the run with it - and
        // Fabric API came from where the build put it, so the copy that ran
        // is ash's.
        assertReportSays("\"third_party_mods\": " + WITH_SODIUM);
        assertReportSays("{ \"id\": \"fabric-api\", \"copy\": \"ash\" }");

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

            joinIsRecorded(context);
            toggleSprintWorks(context, server);
            freelookWorks(context, server);
            snaplookWorks(context);
            pingReadoutWorks(context);
            settingsScreenWorks(context);
            crosshairOptionsWork(context);
            crosshairWorks(context);
            hitIndicatorWorks(context, server);
            hitColourWorks(context, server);
            moveReadoutsWorks(context);
            panelIsCrispAtEveryGuiScale(context);
        }
    }

    /**
     * ash's settings screen, opened with its key and pressed with the mouse,
     * as a player would: switching the FPS readout off reaches the file, the
     * load report and the HUD, and the same key - then Escape - closes it.
     */
    private static void settingsScreenWorks(ClientGameTestContext context) {
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        boolean opened = context.computeOnClient(client -> client.screen instanceof AshSettingsScreen);
        if (!opened) {
            throw new AssertionError("Right Shift did not open ash's settings");
        }
        context.takeScreenshot("ash-settings-panel");

        clickSwitch(context, Feature.FPS_READOUT);
        context.waitTicks(5);
        assertFileSays("fps-readout.enabled=false");
        assertReportSays("{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"off\" }");
        assertReadoutDraws(context, false, "the FPS readout still draws after it was switched off");
        // By eye: no frame rate top-left, the marker still bottom-left, the
        // HUD readable through the screen.
        context.takeScreenshot("ash-settings-fps-readout-off");

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        if (context.computeOnClient(client -> client.screen != null)) {
            throw new AssertionError("the key that opened ash's settings did not close them");
        }

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickSwitch(context, Feature.FPS_READOUT);
        context.waitTicks(5);
        assertFileSays("fps-readout.enabled=true");
        assertReadoutDraws(context, true, "the FPS readout did not come back when it was switched on");
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        if (context.computeOnClient(client -> client.screen != null)) {
            throw new AssertionError("Escape did not close ash's settings");
        }
    }

    /**
     * ash's crosshair, in a real world, judged by pixels against a frame with
     * no crosshair in it at all (F1).
     *
     * <p>On: the centre and all four arm tips are exactly ash's white, and
     * the pixel past a tip is exactly its outline. ash's default plus covers
     * the game's own pixel for pixel - arms of four around the same centre -
     * so the game's crosshair drawn as well would have inverted them.
     *
     * <p>Off, on the settings screen: the game's crosshair is back, which
     * inverts what is behind it - so its centre is the inverse of the same
     * pixel with no crosshair. "Not white" would not do: it is also what no
     * crosshair at all looks like.
     */
    private static void crosshairWorks(ClientGameTestContext context) {
        boolean drew = context.computeOnClient(client -> {
            RecordingSurface surface = new RecordingSurface();
            return CrosshairHook.draw(surface, 0, 0) && surface.fills > 0;
        });
        if (!drew) {
            throw new AssertionError("ash's crosshair is on but drew nothing");
        }

        Frame none = frame(context, "ash-crosshair-none", true);
        Frame ash = frame(context, "ash-crosshair", false);
        for (int[] at : new int[][] {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            ash.expect(at[0], at[1], 0xFFFFFF, "ash's white crosshair");
        }
        ash.expect(5, 0, 0x000000, "ash's crosshair's outline");

        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        switchCrosshair(context, settingsKey, "crosshair.enabled=false");
        if (context.computeOnClient(client -> CrosshairHook.draw(new RecordingSurface(), 0, 0))) {
            throw new AssertionError("ash's crosshair still draws after it was switched off");
        }

        Frame game = frame(context, "ash-crosshair-off", false);
        for (int[] at : new int[][] {{0, 0}, {4, 0}}) {
            game.expectInverseOf(none, at[0], at[1]);
        }

        switchCrosshair(context, settingsKey, "crosshair.enabled=true");
    }

    /**
     * The hit indicator, against the test's own server. Its options are set
     * on the settings screen first - the longest duration, so the mark is
     * still up when it is looked for. Then a swing at the empty sky, which
     * must not mark: a click is not a hit. Then a pig, looked at and hit,
     * which must: the server's damage event names this player as its cause.
     */
    private static void hitIndicatorWorks(ClientGameTestContext context, TestDedicatedServerContext server) {
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickOn(context, "the hit indicator's options link", panel -> panel.optionsLinkOf(Feature.HIT_INDICATOR));
        clickOn(context, "1000 ms on its slider", panel -> panel.sliderAt(Settings.HIT_INDICATOR_DURATION, 1000));
        clickOn(context, "the colour's chip", panel -> panel.colourChipOf(Settings.HIT_INDICATOR_COLOUR));
        clickOn(context, "the green swatch", panel -> panel.swatchOf(Settings.HIT_INDICATOR_COLOUR, 0x4DFF88));
        // By eye: the page, with Duration at 1000 ms and green chosen.
        context.takeScreenshot("ash-hit-indicator-options");
        assertFileSays("hit-indicator.duration=1000");
        assertFileSays("hit-indicator.colour=#4DFF88FF");
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);

        server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ -90");
        context.waitTicks(10);
        context.getInput().pressKey(options -> options.keyAttack);
        context.waitTicks(1);
        // The swing itself, or "no mark" would hold for a press that never arrived.
        if (!context.computeOnClient(client -> client.player.swinging)) {
            throw new AssertionError("the attack key did not swing at the sky, so its no-mark check proves nothing");
        }
        context.waitTicks(9);
        if (hitIndicatorShows(context)) {
            throw new AssertionError("a swing at the empty sky lit the hit indicator");
        }

        // Level with the player, so the pig is two blocks straight ahead, and
        // NoAI so it is still there when the swing arrives.
        server.runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:pig ^ ^ ^2 {NoAI:1b,Tags:[\"ash_target\"]}");
        // Anchored at the eyes, or the angle is worked out from the feet and
        // the crosshair passes over the pig.
        server.runCommand("execute as @a at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=ash_target,limit=1] eyes");
        context.waitTicks(10);
        String aim = context.computeOnClient(client -> client.hitResult instanceof EntityHitResult ? null
                : "the player at " + client.player.position() + " facing " + client.player.getYRot() + ","
                        + client.player.getXRot() + "; pigs the client knows of: "
                        + java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                                .filter(entity -> entity.getType() == net.minecraft.world.entity.EntityType.PIG)
                                .map(entity -> String.valueOf(entity.position())).toList()
                        + "; under the crosshair: " + client.hitResult);
        if (aim != null) {
            throw new AssertionError("the pig is not under the crosshair, so the hit below would prove nothing - " + aim);
        }
        context.getInput().pressKey(options -> options.keyAttack);
        context.waitTicks(4);
        if (!hitIndicatorShows(context)) {
            throw new AssertionError("a hit on the pig, confirmed by the server, did not light the hit indicator");
        }
        // By eye: four short green diagonals around the crosshair, on the pig.
        context.takeScreenshot("ash-hit-indicator");
        server.runCommand("kill @e[tag=ash_target]");
    }

    /**
     * ash's panel draws in the screen's real pixels, so it looks exactly the
     * same at every GUI scale: the white pixels of its SETTINGS letters, and
     * the bright pixels of the Crosshair tile's icon, are the same
     * pixels at scales 1, 2, 3 and Auto. The window is made 1280 by
     * 720 first, the smallest at which the game offers scale 3.
     */
    private static void panelIsCrispAtEveryGuiScale(ClientGameTestContext context) {
        int[] before = context.computeOnClient(client -> new int[] {
            client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()});
        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(5);
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        java.util.Set<Long> first = null;
        java.util.Set<Long> firstIcon = null;
        for (int scale : new int[] {1, 2, 3, 0}) {
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeDisplay();
            });
            context.waitTicks(3);
            context.getInput().pressKey(settingsKey);
            context.waitTicks(5);
            settle(context);
            Rect[] areas = context.computeOnClient(client -> {
                if (!(client.screen instanceof AshSettingsScreen screen)) {
                    throw new AssertionError("ash's settings did not open at GUI scale " + scale);
                }
                return new Rect[] {screen.panel().lettersArea(), screen.panel().tileIconOf(Feature.CROSSHAIR)};
            });
            Path shot = context.takeScreenshot("ash-panel-gui-scale-" + (scale == 0 ? "auto" : scale));
            context.getInput().pressKey(settingsKey);
            context.waitTicks(5);
            Rect letters = areas[0];
            java.util.Set<Long> white = whitePixels(shot, letters, 240);
            java.util.Set<Long> icon = whitePixels(shot, areas[1], 170);
            if (icon.size() < 30) {
                throw new AssertionError("the Crosshair tile has no icon at GUI scale " + scale + ": " + icon.size()
                        + " bright pixels in " + areas[1]);
            }
            if (firstIcon == null) {
                firstIcon = icon;
            } else if (!firstIcon.equals(icon)) {
                throw new AssertionError("at GUI scale " + (scale == 0 ? "Auto" : scale)
                        + " the Crosshair icon is not the same pixels as at GUI scale 1");
            }
            if (white.size() < 50) {
                throw new AssertionError("SETTINGS is not on the panel at GUI scale " + scale + ": " + white.size()
                        + " white pixels in " + letters);
            }
            if (first == null) {
                first = white;
            } else if (!first.equals(white)) {
                throw new AssertionError("at GUI scale " + (scale == 0 ? "Auto" : scale)
                        + " SETTINGS is not the same pixels as at GUI scale 1: the panel is not drawn at real resolution");
            }
        }
        context.runOnClient(client -> {
            client.options.guiScale().set(0);
            client.resizeDisplay();
        });
        context.getInput().resizeWindow(before[0], before[1]);
        context.waitTicks(5);
    }

    /** Where a screenshot is at least {@code level} in every channel, inside a rectangle of real pixels, as points relative to it. */
    private static java.util.Set<Long> whitePixels(Path shot, Rect area, int level) {
        BufferedImage image;
        try {
            image = ImageIO.read(shot.toFile());
        } catch (IOException unreadable) {
            throw new AssertionError("could not read the screenshot " + shot, unreadable);
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

    /** Whether the hit indicator draws a mark right now, asked of the feature itself. */
    private static boolean hitIndicatorShows(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.hitIndicator.draw(surface, 0, 0);
            return surface.fills > 0;
        });
    }

    /** Opens ash's settings, presses one switch, checks the file, and closes them again. */
    private static void switchCrosshair(ClientGameTestContext context, KeyMapping settingsKey, String fileSays) {
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickSwitch(context, Feature.CROSSHAIR);
        context.waitTicks(5);
        assertFileSays(fileSays);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        if (context.computeOnClient(client -> client.screen != null)) {
            throw new AssertionError("ash's settings did not close, so the next frame is not of the world");
        }
    }

    /**
     * A screenshot, optionally with the HUD hidden, and where the game's own
     * crosshair centre is in it: ((width - 15) / 2 + 7, (height - 15) / 2 +
     * 7) in GUI units, as the game places its 15-square sprite.
     */
    private static Frame frame(ClientGameTestContext context, String name, boolean hudHidden) {
        context.runOnClient(client -> client.options.hideGui = hudHidden);
        context.waitTicks(3);
        int[] geometry = context.computeOnClient(client -> {
            var window = client.getWindow();
            return new int[] {
                (window.getGuiScaledWidth() - 15) / 2 + 7, (window.getGuiScaledHeight() - 15) / 2 + 7, window.getGuiScale()
            };
        });
        Path shot = context.takeScreenshot(name);
        context.runOnClient(client -> client.options.hideGui = false);
        try {
            return new Frame(ImageIO.read(shot.toFile()), geometry[0], geometry[1], geometry[2], name);
        } catch (IOException unreadable) {
            throw new AssertionError("could not read the screenshot " + shot, unreadable);
        }
    }

    /** One screenshot, read by GUI pixel around the crosshair's centre. */
    private record Frame(BufferedImage image, int centreX, int centreY, int scale, String name) {

        /** The colour of the middle of the GUI pixel this far from the centre. */
        int at(int dx, int dy) {
            return image.getRGB((centreX + dx) * scale + scale / 2, (centreY + dy) * scale + scale / 2) & 0xFFFFFF;
        }

        void expect(int dx, int dy, int colour, String what) {
            if (at(dx, dy) != colour) {
                throw new AssertionError(name + ": at " + dx + "," + dy + " from the centre, expected " + what
                        + " #" + Integer.toHexString(colour) + " but found #" + Integer.toHexString(at(dx, dy)));
            }
        }

        /** Each channel within a few levels of the inverse: the game's crosshair over the same world. */
        void expectInverseOf(Frame without, int dx, int dy) {
            int found = at(dx, dy);
            int inverse = ~without.at(dx, dy) & 0xFFFFFF;
            for (int shift = 0; shift <= 16; shift += 8) {
                if (Math.abs(((found >> shift) & 0xFF) - ((inverse >> shift) & 0xFF)) > 24) {
                    throw new AssertionError(name + ": at " + dx + "," + dy + " from the centre, expected the game's"
                            + " inverting crosshair, #" + Integer.toHexString(inverse) + ", but found #"
                            + Integer.toHexString(found) + " - with none it is #"
                            + Integer.toHexString(without.at(dx, dy)));
                }
            }
        }
    }

    /** A binding by name, checked against every other binding's default key. */
    /**
     * Freelook, held with its key, with the real mixins and the real server:
     * the camera turns, the player does not - on the client or as the server
     * was told - and a screen opening mid-hold ends it cleanly.
     *
     * <p>The mouse is moved through the game's own {@code turnPlayer}, the
     * method freelook's mixin wraps, with the movement set where the game's
     * cursor callback leaves it. The framework's {@code moveCursor} cannot be
     * relied on here: the game turns on a mouse move only while its window is
     * active, and under Xvfb it never is - so a test that moved the cursor
     * could pass by nothing turning at all.
     */
    /**
     * Joining the test's server is in the record the launcher's servers card
     * reads, under the address the game itself holds for it.
     */
    private static void joinIsRecorded(ClientGameTestContext context) {
        String address = context.computeOnClient(c -> c.getCurrentServer() == null ? null : c.getCurrentServer().ip);
        if (address == null) {
            throw new AssertionError("the game holds no address for the server it joined");
        }
        List<RecentServers.Join> joins = RecentServers.read(
                FabricLoader.getInstance().getGameDir().resolve(RecentServers.RELATIVE_PATH));
        if (joins.isEmpty() || !joins.get(0).address.equals(address)) {
            throw new AssertionError("joining " + address + " was not recorded first in the recent servers");
        }
    }

    private static void freelookWorks(ClientGameTestContext context, TestDedicatedServerContext server) {
        KeyMapping key = binding(context, Freelook.BINDING_NAME);
        float[] before = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot()});

        context.getInput().holdKey(key);
        context.waitTicks(2);
        if (!context.computeOnClient(c -> AshClient.freelook.active())) {
            throw new AssertionError("holding the freelook key did not start freelook");
        }
        if (context.computeOnClient(c -> c.options.getCameraType()) != CameraType.THIRD_PERSON_BACK) {
            throw new AssertionError("freelook held in first person did not move the view behind the player");
        }

        context.runOnClient(AshLoadsGameTest::turnTheMouse);
        context.waitTicks(5);

        float[] after = context.computeOnClient(c -> new float[] {c.player.getYRot(), c.player.getXRot(),
                c.gameRenderer.getMainCamera().yRot(), AshClient.freelook.yaw()});
        if (after[0] != before[0] || after[1] != before[1]) {
            throw new AssertionError("freelook turned the player: " + before[0] + "," + before[1] + " became "
                    + after[0] + "," + after[1]);
        }
        if (Math.abs(after[3] - before[0]) < 10) {
            throw new AssertionError("the mouse did not turn freelook's camera: yaw " + after[3]);
        }
        if (Math.abs(after[2] - after[3]) > 0.5) {
            throw new AssertionError("the camera is not at freelook's angle: " + after[2] + " against " + after[3]);
        }
        float seenByServer = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getYRot());
        if (Math.abs(seenByServer - before[0]) > 0.01) {
            throw new AssertionError("the server was told the camera's turn: it has the player at " + seenByServer
                    + ", not " + before[0]);
        }
        context.takeScreenshot("ash-freelook");

        // A screen opening mid-hold ends it and puts the view back, while the
        // screen is open. (The game releases every key for a screen and reads
        // the keyboard again when it closes, so a key still held then is a new
        // press, as it is for sneak; the test lets go first.)
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(2);
        if (context.computeOnClient(c -> AshClient.freelook.active())) {
            throw new AssertionError("freelook outlived a screen opening mid-hold");
        }
        if (context.computeOnClient(c -> c.options.getCameraType()) != CameraType.FIRST_PERSON) {
            throw new AssertionError("ending freelook did not put the first-person view back");
        }
        context.getInput().releaseKey(key);
        context.setScreen(() -> null);
        context.waitTicks(2);
        if (context.computeOnClient(c -> AshClient.freelook.active())) {
            throw new AssertionError("freelook came back with its key let go");
        }
        float cameraYaw = context.computeOnClient(c -> c.gameRenderer.getMainCamera().yRot());
        if (Math.abs(cameraYaw - before[0]) > 0.5) {
            throw new AssertionError("the camera did not return to where the player looks: " + cameraYaw);
        }
    }

    /**
     * Snaplook, held with its key: the game's own front view while it is down,
     * and back to the view the player had - first person, and third person
     * from behind - when it is let go.
     */
    private static void snaplookWorks(ClientGameTestContext context) {
        KeyMapping key = binding(context, Snaplook.BINDING_NAME);
        for (CameraType had : new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
            context.runOnClient(c -> c.options.setCameraType(had));
            context.getInput().holdKey(key);
            context.waitTicks(2);
            if (context.computeOnClient(c -> c.options.getCameraType()) != CameraType.THIRD_PERSON_FRONT) {
                throw new AssertionError("holding the snaplook key in " + had + " did not show the front view");
            }
            if (had == CameraType.FIRST_PERSON) {
                context.takeScreenshot("ash-snaplook");
            }
            context.getInput().releaseKey(key);
            context.waitTicks(2);
            CameraType back = context.computeOnClient(c -> c.options.getCameraType());
            if (back != had) {
                throw new AssertionError("letting go of snaplook left " + back + ", not the " + had + " it had");
            }
        }
        context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /** A mouse move, as the cursor callback would leave it, handed to the game's own turn. */
    private static void turnTheMouse(Minecraft client) {
        try {
            Field dx = MouseHandler.class.getDeclaredField("accumulatedDX");
            dx.setAccessible(true);
            dx.setDouble(client.mouseHandler, 400);
            Method turn = MouseHandler.class.getDeclaredMethod("turnPlayer", double.class);
            turn.setAccessible(true);
            turn.invoke(client.mouseHandler, 1.0);
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError("the game's mouse handler is not as research 0009 read it", unreachable);
        }
    }

    private static KeyMapping binding(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            KeyMapping found = null;
            for (KeyMapping mapping : client.options.keyMappings) {
                if (mapping.getName().equals(name)) {
                    found = mapping;
                }
            }
            if (found == null) {
                throw new AssertionError("no \"" + name + "\" binding in Controls");
            }
            for (KeyMapping other : client.options.keyMappings) {
                if (other != found && other.getDefaultKey().equals(found.getDefaultKey())) {
                    throw new AssertionError(name + "'s default key is also " + other.getName() + "'s");
                }
            }
            return found;
        });
    }

    /**
     * The crosshair's options page, used as a player would: open it from the
     * card, choose a shape, set a size on the slider, pick a colour, and the
     * file and the crosshair the game draws both follow. Then "Reset to defaults",
     * which the crosshair check after this one relies on.
     */
    private static void crosshairOptionsWork(ClientGameTestContext context) {
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);

        clickOn(context, "the crosshair's options link", panel -> panel.optionsLinkOf(Feature.CROSSHAIR));
        clickOn(context, "the dot", panel -> panel.choiceOf(Settings.CROSSHAIR_SHAPE, "dot"));
        clickOn(context, "size 6 on its slider", panel -> panel.sliderAt(Settings.CROSSHAIR_SIZE, 6));
        clickOn(context, "the colour's chip", panel -> panel.colourChipOf(Settings.CROSSHAIR_COLOUR));
        clickOn(context, "the red swatch", panel -> panel.swatchOf(Settings.CROSSHAIR_COLOUR, 0xFF4D4D));
        // By eye: the page, with a red dot in all three previews.
        context.takeScreenshot("ash-crosshair-options");

        assertFileSays("crosshair.shape=dot");
        assertFileSays("crosshair.size=6");
        assertFileSays("crosshair.colour=#FF4D4DFF");

        // The panel closed, the world's own frame: a red square six wide,
        // from three left of the centre to two right of it, outlined.
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        Frame dot = frame(context, "ash-crosshair-red-dot", false);
        for (int[] at : new int[][] {{0, 0}, {2, 2}, {-3, -3}, {2, -3}}) {
            dot.expect(at[0], at[1], 0xFF4D4D, "the red dot");
        }
        dot.expect(3, 0, 0x000000, "the dot's outline");
        dot.expect(-4, 0, 0x000000, "the dot's outline");

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickOn(context, "the crosshair's options link", panel -> panel.optionsLinkOf(Feature.CROSSHAIR));
        clickOn(context, "Reset to defaults", Panel::resetToDefaults);
        assertFileSays("crosshair.shape=cross");
        assertFileSays("crosshair.size=4");
        assertFileSays("crosshair.colour=#FFFFFFFF");
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
    }

    /** Moves the real cursor onto a feature's switch on ash's panel and clicks, as a player would. */
    /**
     * Hit colour, set on ash's panel with the mouse: blue at 60 per cent
     * reaches the overlay texture's red rows at once, a hurt pig on the test's
     * server flashes it, and switching the feature off puts back the game's
     * own red exactly.
     */
    private static void hitColourWorks(ClientGameTestContext context, TestDedicatedServerContext server) {
        if (context.computeOnClient(c -> HitColourTexture.redRowTexel()) != HitColourTexture.GAME_TEXEL) {
            throw new AssertionError("before any change, the flash is not the game's own red");
        }
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickOn(context, "hit colour's options link", panel -> panel.optionsLinkOf(Feature.HIT_COLOUR));
        clickOn(context, "the colour's chip", panel -> panel.colourChipOf(Settings.HIT_COLOUR_COLOUR));
        clickOn(context, "the blue swatch", panel -> panel.swatchOf(Settings.HIT_COLOUR_COLOUR, 0x4DC3FF));
        clickOn(context, "60 on the strength slider", panel -> panel.sliderAt(Settings.HIT_COLOUR_STRENGTH, 60));
        assertFileSays("hit-colour.colour=#4DC3FF");
        assertFileSays("hit-colour.strength=60");
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);

        // 60 per cent blue keeps 40 per cent of the entity's colour: 102 of 255.
        int texel = context.computeOnClient(c -> HitColourTexture.redRowTexel());
        if (texel != 0x664DC3FF) {
            throw new AssertionError("the flash's texel is " + Integer.toHexString(texel) + ", not 664dc3ff");
        }

        // By eye: a pig in front of the player, flashing blue.
        server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~ 20");
        server.runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:pig ^ ^ ^3 {NoAI:1b,Tags:[\"ash_flash\"]}");
        context.waitTicks(10);
        server.runCommand("damage @e[tag=ash_flash,limit=1] 1");
        context.waitTicks(2);
        context.takeScreenshot("ash-hit-colour");
        server.runCommand("kill @e[tag=ash_flash]");

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickSwitch(context, Feature.HIT_COLOUR);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        if (context.computeOnClient(c -> HitColourTexture.redRowTexel()) != HitColourTexture.GAME_TEXEL) {
            throw new AssertionError("switching hit colour off did not put back the game's own red");
        }
    }

    /**
     * Edit HUD, as a player uses it: opened from the panel, the FPS readout
     * taken by the real cursor and dragged to the bottom-right of the window,
     * saved there and drawn there; then Reset puts it back and Done returns
     * to the panel.
     */
    private static void moveReadoutsWorks(ClientGameTestContext context) {
        int left = org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
        KeyMapping settingsKey = binding(context, SettingsScreen.BINDING_NAME);
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        clickOn(context, "Edit HUD", Panel::editHudButton);
        // A frame or two for the readouts, drawn beneath, to say how big they are.
        context.waitTicks(3);

        double[] from = cursorAt(context, "the FPS readout's box", panel -> panel.readoutBox(HudLayout.Readout.FPS));
        double[] window = context.computeOnClient(client ->
                new double[] {client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()});
        context.getInput().setCursorPos(from[0], from[1]);
        context.getInput().holdMouse(left);
        context.waitTicks(2);
        context.getInput().setCursorPos(window[0] * 0.6, window[1] * 0.6);
        context.waitTicks(2);
        context.getInput().setCursorPos(window[0] * 0.95, window[1] * 0.95);
        context.waitTicks(3);
        // By eye: the box in the bottom-right, its tag saying so.
        context.takeScreenshot("ash-edit-hud");
        context.getInput().releaseMouse(left);
        context.waitTicks(3);

        assertFileHasLineStarting("fps-readout.position=bottom-right ");
        int[] drawnAt = context.computeOnClient(client -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.fpsReadout.draw(surface);
            return new int[] {surface.lastX, surface.lastY};
        });
        if (drawnAt[0] < 427 / 2 || drawnAt[1] < 240 / 2) {
            throw new AssertionError("the FPS readout was dropped bottom-right but draws at " + drawnAt[0] + ","
                    + drawnAt[1] + " of 427 by 240");
        }

        clickOn(context, "Edit HUD's Reset", Panel::resetReadoutsButton);
        assertFileSays("fps-readout.position=top-left 4 4");
        clickOn(context, "Edit HUD's Done", Panel::doneButton);
        boolean stillEditing = context.computeOnClient(client ->
                client.screen instanceof AshSettingsScreen screen && screen.panel().editingHud());
        if (stillEditing) {
            throw new AssertionError("Done did not go back to the panel");
        }
        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
    }

    private static void clickSwitch(ClientGameTestContext context, Feature feature) {
        clickOn(context, feature + "'s switch", panel -> panel.switchOf(feature));
    }

    /**
     * Moves the real cursor onto something on ash's panel and clicks, as a
     * player would: the panel says where it drew it, and the game's own input
     * does the rest.
     */
    private static void clickOn(ClientGameTestContext context, String what,
            java.util.function.Function<Panel, Rect> where) {
        double[] at = cursorAt(context, what, where);
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    /**
     * Waits until ash's panel has stopped moving (#66) - opening, a page
     * changing, tiles arriving - as a player's eye waits for things to land
     * before clicking. By the clock rather than by ticks, since the framework
     * decides how fast ticks pass.
     */
    private static void settle(ClientGameTestContext context) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (context.computeOnClient(client ->
                client.screen instanceof AshSettingsScreen screen && screen.panel().animating())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("ash's panel was still moving after 10 seconds");
            }
            context.waitTicks(1);
        }
    }

    /** Where the cursor goes to be over something on ash's panel, in window coordinates. */
    private static double[] cursorAt(ClientGameTestContext context, String what,
            java.util.function.Function<Panel, Rect> where) {
        settle(context);
        return context.computeOnClient(client -> {
            if (!(client.screen instanceof AshSettingsScreen screen)) {
                throw new AssertionError("ash's settings are not open");
            }
            Rect target = where.apply(screen.panel());
            if (target == null) {
                throw new AssertionError("ash's panel does not show " + what);
            }
            // The panel is drawn in real pixels; the cursor is in window
            // coordinates, which differ from them only on a high-DPI screen.
            var window = client.getWindow();
            return new double[] {(target.centreX() + 0.5) * window.getScreenWidth() / window.getWidth(),
                (target.centreY() + 0.5) * window.getScreenHeight() / window.getHeight()};
        });
    }

    /**
     * The ping readout, against the test's dedicated server: it says exactly
     * the latency the server gave the player's own tab-list entry.
     */
    private static void pingReadoutWorks(ClientGameTestContext context) {
        String result = context.computeOnClient(client -> {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info == null) {
                return "the server has not listed the player";
            }
            RecordingSurface surface = new RecordingSurface();
            AshClient.pingReadout.draw(surface);
            String expected = info.getLatency() + " ms";
            return surface.drawn.equals(List.of(expected)) ? null : "drew " + surface.drawn + ", not " + expected;
        });
        if (result != null) {
            throw new AssertionError("the ping readout does not show the tab list's number: " + result);
        }
        context.takeScreenshot("ash-ping-readout");
    }

    private static void assertReadoutDraws(ClientGameTestContext context, boolean expected, String otherwise) {
        List<String> drawn = context.computeOnClient(client -> {
            RecordingSurface surface = new RecordingSurface();
            AshClient.fpsReadout.draw(surface);
            return surface.drawn;
        });
        if (drawn.isEmpty() == expected) {
            throw new AssertionError(otherwise + " (drew " + drawn + ")");
        }
    }

    private static void assertFileSays(String line) {
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        try {
            String written = Files.readString(settings);
            if (!written.contains("\n" + line + "\n")) {
                throw new AssertionError("ash.properties does not say " + line + ":\n" + written);
            }
        } catch (IOException unreadable) {
            throw new AssertionError("ash.properties could not be read", unreadable);
        }
    }

    private static void assertFileHasLineStarting(String start) {
        Path settings = FabricLoader.getInstance().getConfigDir().resolve("ash.properties");
        try {
            for (String line : Files.readAllLines(settings)) {
                if (line.startsWith(start)) {
                    return;
                }
            }
            throw new AssertionError("ash.properties has no line starting " + start + ":\n"
                    + Files.readString(settings));
        } catch (IOException unreadable) {
            throw new AssertionError("ash.properties could not be read", unreadable);
        }
    }

    private static void assertReportSays(String entry) {
        Path report = FabricLoader.getInstance().getGameDir().resolve(LoadReport.RELATIVE_PATH);
        try {
            String written = Files.readString(report);
            if (!written.contains(entry)) {
                throw new AssertionError("the load report does not say " + entry + ":\n" + written);
            }
        } catch (IOException unreadable) {
            throw new AssertionError("the load report could not be read", unreadable);
        }
    }

    /**
     * Toggle sprint, driven through the same input a player's keyboard feeds,
     * with the real mixin, the real latch and the real server.
     *
     * <p>The server's view is checked as well as the client's, because it is
     * the one the feature's promise is about. Since 1.21.2 the client sends
     * the player's keys to the server every tick, and the server keeps the
     * last set it received - so this asks the server directly what it was
     * told the sprint key was doing, as well as whether the player sprints.
     * The one place that answer must differ from the latch is a menu: the game
     * releases every key when a screen opens, and a vanilla client in an
     * inventory cannot send "sprint held".
     */
    private static void toggleSprintWorks(ClientGameTestContext context, TestDedicatedServerContext server) {
        KeyMapping toggle = binding(context, ToggleSprint.BINDING_NAME);

        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        assertSprinting(context, server, false, "holding forward alone started a sprint");

        context.getInput().pressKey(toggle);
        context.waitTicks(20);
        assertSprinting(context, server, true, "a press of the toggle key did not start a sprint");
        assertServerWasSent(context, server, true, "the server was not sent the sprint key held while it was toggled on");

        // A menu, with the latch still on. The game releases every key when a
        // screen opens; the latch must read as released with them.
        context.getInput().releaseKey(options -> options.keyUp);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(10);
        assertServerWasSent(context, server, false, "the server was sent the sprint key held from inside an inventory");

        // And back out: the latch outlived the menu, and holding forward
        // sprints again without another press.
        context.setScreen(() -> null);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        assertSprinting(context, server, true, "closing a menu lost the latch");

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

    /** What the server was last told the sprint key was doing, and what the client built. */
    private static void assertServerWasSent(
            ClientGameTestContext context, TestDedicatedServerContext server, boolean expected, String otherwise) {
        boolean built = context.computeOnClient(c -> c.player.input.keyPresses.sprint());
        boolean received = server.computeOnServer(
                s -> s.getPlayerList().getPlayers().get(0).getLastClientInput().sprint());
        if (built != expected || received != expected) {
            throw new AssertionError(otherwise + " (client built: " + built + ", server received: " + received + ")");
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
