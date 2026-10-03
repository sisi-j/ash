package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.ui.Rect;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

        // And the load report, which the launcher reads before the next play.
        // In a build where every mixin lands, every feature reports loaded -
        // and a mixin that stopped matching this game version fails here, by
        // feature, with the game still running rather than crashed.
        for (String feature : new String[] {
            "{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"loaded\" }",
            "{ \"id\": \"toggle-sprint\", \"name\": \"Toggle sprint\", \"status\": \"loaded\" }",
            "{ \"id\": \"crosshair\", \"name\": \"Crosshair\", \"status\": \"loaded\" }",
            "{ \"id\": \"hit-indicator\", \"name\": \"Hit indicator\", \"status\": \"loaded\" }",
            "{ \"id\": \"settings-screen\", \"name\": \"ash's settings screen\", \"status\": \"loaded\" }",
        }) {
            assertReportSays(feature);
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
            settingsScreenWorks(context);
            crosshairOptionsWork(context);
            crosshairWorks(context);
            hitIndicatorWorks(context, server);
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
        double[] at = context.computeOnClient(client -> {
            if (!(client.screen instanceof AshSettingsScreen screen)) {
                throw new AssertionError("ash's settings are not open");
            }
            Rect target = where.apply(screen.panel());
            if (target == null) {
                throw new AssertionError("ash's panel does not show " + what);
            }
            double scale = client.getWindow().getGuiScale();
            return new double[] {(target.centreX() + 0.5) * scale, (target.centreY() + 0.5) * scale};
        });
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
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
        int fills;

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
