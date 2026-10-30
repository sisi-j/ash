package com.ashlauncher.client.v26_3;

import com.ashlauncher.client.Beta;
import com.ashlauncher.client.hud.HudItem;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.ui.Rect;
import com.ashlauncher.client.report.Feature;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;

/**
 * A real vanilla 26.3 client, launched, with ash in it (ADR-0028).
 *
 * <p>What 26.3's test asks, as spec 0007 sets it: that everything loads, that
 * the player wears ash's cape, and that ash's settings open - with a search
 * typed into them, since 26.3's SDL input hands typing only to a screen that
 * has started text input, and Fabric's typing helper skips SDL and would pass
 * either way (research 0016, 5.6). 1.21.11's test walks every feature; this
 * one grows as 26.3 does.
 */
public class AshLoadsGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // ash's CDN, for the cape test, from before the game first asks who
        // wears what: a profile that wasn't there is not asked again for a while.
        try (TestCdn cdn = new TestCdn()) {
            runTestWithCdn(context);
        } catch (IOException couldNotStart) {
            throw new AssertionError("the test's CDN did not start on port " + TestCdn.PORT, couldNotStart);
        }
    }

    private void runTestWithCdn(ClientGameTestContext context) {
        context.waitTicks(20);

        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            throw new AssertionError("the vanilla client started without ash in it, which is the one thing this tier is for");
        }
        if (!Beta.on()) {
            throw new AssertionError("the game test ran as a release build, so no beta work was tested");
        }
        // Every feature 26.3 has, loaded, with the game still running: a mixin
        // that stopped matching would say "degraded" here.
        for (Feature feature : Feature.values()) {
            String status = feature.existsOn(com.ashlauncher.client.report.Target.V26_3) ? "loaded" : "unsupported";
            assertReportSays("{ \"id\": \"" + feature.id() + "\", \"name\": \"" + feature.displayName()
                    + "\", \"status\": \"" + status + "\" }");
        }
        context.takeScreenshot("ash-loaded");

        context.runOnClient(client -> client.options.pauseOnLostFocus = false);
        // A dedicated server rather than a singleplayer world, as on 1.21.11:
        // it does not pause while its player list is empty, so the world loads.
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
                TestDedicatedServerConnection connection = server.connect()) {
            connection.waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("ash-in-world");

            capeWorks(context);
            settingsScreenWorks(context);
            snappingWorks(context);
        }
    }

    /** The player's ash cape, from the test's CDN, in the skin the game draws them with. */
    private static void capeWorks(ClientGameTestContext context) {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!context.computeOnClient(c -> {
            var cape = c.player.getSkin().cape();
            return cape != null && cape.texturePath().getNamespace().equals("ash");
        })) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the player's skin never carried ash's cape from " + TestCdn.CAPE);
            }
            context.waitTicks(2);
        }
        context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(5);
        context.takeScreenshot("ash-cape");
        context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * ash's settings, opened with their key: text input started for the
     * panel, a search typed into it that finds an option's tile, Escape
     * clearing the search and then closing the panel - and text input stopped
     * again once it has.
     */
    private static void settingsScreenWorks(ClientGameTestContext context) {
        context.getInput().pressKey(binding(context, SettingsScreen.BINDING_NAME));
        context.waitTicks(5);
        settle(context);
        boolean opened = context.computeOnClient(client -> client.gui.screen() instanceof AshSettingsScreen);
        if (!opened) {
            throw new AssertionError("Right Shift did not open ash's settings");
        }
        context.takeScreenshot("ash-settings-panel");
        Object owner = context.computeOnClient(client -> textInputOwner(client.textInputManager()));
        Object screen = context.computeOnClient(client -> client.gui.screen());
        if (owner != screen) {
            throw new AssertionError("ash's settings did not start SDL text input, so a player could not type a"
                    + " search: its owner is " + owner);
        }

        context.getInput().typeChars("gap");
        context.waitTicks(3);
        settle(context);
        String query = context.computeOnClient(client -> ((AshSettingsScreen) client.gui.screen()).panel().query());
        boolean crosshairShown = context.computeOnClient(
                client -> ((AshSettingsScreen) client.gui.screen()).panel().switchOf(Feature.CROSSHAIR) != null);
        boolean fpsShown = context.computeOnClient(
                client -> ((AshSettingsScreen) client.gui.screen()).panel().switchOf(Feature.FPS_READOUT) != null);
        if (!"gap".equals(query) || !crosshairShown || fpsShown) {
            throw new AssertionError("typing \"gap\" searched for \"" + query + "\" and showed the crosshair "
                    + crosshairShown + ", the FPS readout " + fpsShown);
        }
        context.takeScreenshot("ash-settings-search");

        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        String cleared = context.computeOnClient(client -> client.gui.screen() instanceof AshSettingsScreen panel
                ? panel.panel().query() : null);
        if (!"".equals(cleared)) {
            throw new AssertionError("Escape did not clear the search first: " + cleared);
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            throw new AssertionError("Escape did not close ash's settings once the search was clear");
        }
        Object after = context.computeOnClient(client -> textInputOwner(client.textInputManager()));
        if (after == screen) {
            throw new AssertionError("ash's settings closed and left SDL text input started for themselves");
        }
    }

    /** Whether a saved placement is flush against the right edge: a right-hand anchor, no gap. */
    private static boolean flushRight(String saved) {
        return saved.matches("(top-|bottom-)?right 0 -?\d+");
    }

    /**
     * Edit HUD's snapping (spec 0007): the frame rate, dragged until its right
     * edge is two GUI units short of the screen's, lies flush against it and
     * is saved so: right-anchored, with no gap. Driven through the panel as the
     * screen hands it the mouse, in real pixels. Reset all, asked twice, puts
     * it back.
     */
    private static void snappingWorks(ClientGameTestContext context) {
        context.getInput().pressKey(binding(context, SettingsScreen.BINDING_NAME));
        context.waitTicks(5);
        settle(context);
        context.runOnClient(client -> {
            Panel panel = ((AshSettingsScreen) client.gui.screen()).panel();
            Rect edit = panel.editHudButton();
            panel.mouseClicked(edit.centreX(), edit.centreY());
        });
        // A frame or two for the readouts, drawn beneath, to say how big they are.
        context.waitTicks(3);
        String unsnapped = context.computeOnClient(client -> {
            Panel panel = ((AshSettingsScreen) client.gui.screen()).panel();
            int scale = (int) client.getWindow().getGuiScale();
            Rect box = panel.itemBox(HudItem.FPS);
            if (box == null) {
                return "the FPS readout has no box in Edit HUD";
            }
            int left = box.x / scale + 1;
            int width = box.width / scale - 1;
            int move = (client.getWindow().getGuiScaledWidth() - width - 2 - left) * scale;
            panel.mouseClicked(box.centreX(), box.centreY());
            panel.mouseDragged(box.centreX() + move, box.centreY());
            panel.mouseReleased();
            String saved = AshClient.settings.get(Settings.FPS_READOUT_POSITION).format();
            return flushRight(saved) ? null
                    : "dragged two short of the right edge, it was saved at " + saved;
        });
        if (unsnapped != null) {
            throw new AssertionError(unsnapped);
        }
        context.takeScreenshot("ash-edit-hud-snapped");
        context.runOnClient(client -> {
            Panel panel = ((AshSettingsScreen) client.gui.screen()).panel();
            Rect reset = panel.resetReadoutsButton();
            panel.mouseClicked(reset.centreX(), reset.centreY());
            panel.mouseClicked(reset.centreX(), reset.centreY());
            Rect done = panel.doneButton();
            panel.mouseClicked(done.centreX(), done.centreY());
        });
        String reset = context.computeOnClient(client -> AshClient.settings.get(Settings.FPS_READOUT_POSITION).format());
        if (!reset.equals("top-left 4 4")) {
            throw new AssertionError("Reset all, asked twice, left the frame rate at " + reset);
        }
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(10);
    }

    /** Whom the game's text input is started for: null when it is stopped. Private in 26.3, so read by name. */
    private static Object textInputOwner(TextInputManager manager) {
        try {
            Field enabled = TextInputManager.class.getDeclaredField("textInputEnabled");
            Field owner = TextInputManager.class.getDeclaredField("owner");
            enabled.setAccessible(true);
            owner.setAccessible(true);
            return enabled.getBoolean(manager) ? owner.get(manager) : null;
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError("the game's text input is not as research 0016 read it", unreachable);
        }
    }

    private static KeyMapping binding(ClientGameTestContext context, String name) {
        return context.computeOnClient(client -> {
            for (KeyMapping mapping : client.options.keyMappings) {
                if (mapping.getName().equals(name)) {
                    return mapping;
                }
            }
            throw new AssertionError("no \"" + name + "\" binding in Controls");
        });
    }

    /** Waits until ash's panel has stopped moving, as a player's eye waits for things to land. */
    private static void settle(ClientGameTestContext context) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (context.computeOnClient(client ->
                client.gui.screen() instanceof AshSettingsScreen screen && screen.panel().animating())) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("ash's panel was still moving after 10 seconds");
            }
            context.waitTicks(1);
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
}
