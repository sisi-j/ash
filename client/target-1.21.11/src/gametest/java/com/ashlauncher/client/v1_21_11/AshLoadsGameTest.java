package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.SettingsMenu;
import com.ashlauncher.client.sprint.ToggleSprint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

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
        Path report = FabricLoader.getInstance().getGameDir().resolve(LoadReport.RELATIVE_PATH);
        try {
            String written = Files.readString(report);
            for (String feature : new String[] {
                "{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"loaded\" }",
                "{ \"id\": \"toggle-sprint\", \"name\": \"Toggle sprint\", \"status\": \"loaded\" }",
                "{ \"id\": \"settings-screen\", \"name\": \"The settings screen\", \"status\": \"loaded\" }",
            }) {
                if (!written.contains(feature)) {
                    throw new AssertionError("the load report does not say " + feature + ":\n" + written);
                }
            }
        } catch (IOException missing) {
            throw new AssertionError("the client started without writing its load report", missing);
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
        }
    }

    /**
     * ash's settings screen, opened with its key and pressed with the mouse,
     * as a player would: switching the FPS readout off reaches the file, the
     * load report and the HUD, and the same key - then Escape - closes it.
     */
    private static void settingsScreenWorks(ClientGameTestContext context) {
        KeyMapping settingsKey = binding(context, SettingsMenu.BINDING_NAME);

        context.getInput().pressKey(settingsKey);
        context.waitTicks(5);
        boolean opened = context.computeOnClient(client -> client.screen instanceof AshSettingsScreen);
        if (!opened) {
            throw new AssertionError("Right Shift did not open ash's settings");
        }
        context.takeScreenshot("ash-settings");

        clickButton(context, "FPS readout: On");
        context.waitTicks(5);
        String label = context.computeOnClient(client -> buttonLabels(client.screen));
        if (!label.contains("FPS readout: Off")) {
            throw new AssertionError("the FPS readout's switch did not change: " + label);
        }
        assertFileSays("fps-readout.enabled=false");
        assertReportSays("{ \"id\": \"fps-readout\", \"name\": \"FPS readout\", \"status\": \"off\" }");
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
        clickButton(context, "FPS readout: Off");
        context.waitTicks(5);
        assertFileSays("fps-readout.enabled=true");
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        if (context.computeOnClient(client -> client.screen != null)) {
            throw new AssertionError("Escape did not close ash's settings");
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

    /** Moves the real cursor onto the button with this label and clicks, as a player would. */
    private static void clickButton(ClientGameTestContext context, String label) {
        double[] at = context.computeOnClient(client -> {
            for (var child : client.screen.children()) {
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
                    double scale = client.getWindow().getGuiScale();
                    return new double[] {
                        (widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale
                    };
                }
            }
            throw new AssertionError("no \"" + label + "\" button on the screen: " + buttonLabels(client.screen));
        });
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static String buttonLabels(Screen screen) {
        StringBuilder labels = new StringBuilder();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                labels.append('[').append(widget.getMessage().getString()).append(']');
            }
        }
        return labels.toString();
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
}
