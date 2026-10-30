package com.ashlauncher.client.v1_20_4;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.hud.HudItem;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.HudItemSettings;
import com.ashlauncher.client.settings.Setting;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.ui.Rect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * ash's own client test for 1.20.4, the target Fabric's client game tests
 * don't reach: {@code fabric-client-gametest-api-v1} begins with 1.21.4, so
 * the tier target-1.21.11 has does not exist here (ADR-0027). What exists is
 * a real vanilla client Loom can launch headless; this makes that a test, as
 * target-1.8.9's smoke test does.
 *
 * <p>It waits for the title screen and checks ash loaded every feature; reads
 * the server list the launcher writes; walks into a flat world, where the HUD
 * draws and the player - whose tick toggle sprint hooks - exists; opens ash's
 * settings with its key; waits for the player's ash cape from the test's CDN;
 * keeps screenshots for a human; and stops the game, so the run has an exit
 * code. Which mixins land is {@code AshMixinsLandTest}'s, in seconds, on every
 * build; this is whether a game with them in it actually runs.
 *
 * <p>Never shipped: its own source set and its own mod, unknown to src/main.
 */
public final class AshSmokeTest implements ClientModInitializer {

    /** Per step, and generous: a cold CI runner starting a game is not quick. */
    private static final long STEP_TIMEOUT_MS = 180_000L;

    private static final long POLL_MS = 250L;

    @Override
    public void onInitializeClient() {
        // Watches the client from a thread of its own rather than blocking the
        // one that is starting it.
        Thread watcher = new Thread(AshSmokeTest::run, "ash-smoke-test");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void run() {
        // A first launch - every CI run's - opens the accessibility
        // onboarding screen first. Continued as a player would, which also
        // records it as seen, and then the title screen comes.
        Minecraft client = await("put its first screen up", () -> {
            Minecraft c = Minecraft.getInstance();
            return c != null && (c.screen instanceof TitleScreen || c.screen instanceof AccessibilityOnboardingScreen)
                    ? c : null;
        });
        if (client.screen instanceof AccessibilityOnboardingScreen) {
            onClient(client, () -> {
                client.screen.onClose();
                return null;
            });
        }
        await("put its title screen up", () -> client.screen instanceof TitleScreen ? Boolean.TRUE : null);
        checkAshLoaded();
        serverListAshWroteLoads(client);

        TestCdn cdn;
        try {
            cdn = new TestCdn();
        } catch (IOException couldNotStart) {
            fail("the test's CDN did not start on port " + TestCdn.PORT + ": " + couldNotStart);
            return;
        }

        // Flat, because generation is the slow part of a new world. Not paused
        // on lost focus: under Xvfb the window never has it.
        onClient(client, () -> {
            client.options.pauseOnLostFocus = false;
            client.createWorldOpenFlows().createFreshLevel("ash-smoke-test",
                    new LevelSettings("ash smoke test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(4242L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
            return null;
        });
        await("put the player in a world", () -> client.player != null && client.level != null
                && client.screen == null ? Boolean.TRUE : null);
        pause(3_000L);
        screenshot(client, "ash-in-world.png");

        gameViewSees(client);
        hudStyleShows(client);
        settingsScreenOpensWithItsKey(client);
        capeWorks(client);
        cdn.close();

        System.out.println("ash smoke test: passed");
        onClient(client, () -> {
            client.stop();
            return null;
        });
    }

    private static void checkAshLoaded() {
        if (!FabricLoader.getInstance().isModLoaded("ash")) {
            fail("the vanilla client started without ash in it, which is the one thing this is for");
        }
        Path report = FabricLoader.getInstance().getGameDir().resolve(LoadReport.RELATIVE_PATH);
        String reported;
        try {
            reported = new String(Files.readAllBytes(report), StandardCharsets.UTF_8);
        } catch (IOException missing) {
            fail("ash wrote no load report (" + missing + ")");
            return;
        }
        System.out.println("ash smoke test: load report " + reported.replace('\n', ' '));
        // Every feature 1.20.4 has (faster clouds and view scan are 1.8.9's).
        for (String feature : new String[] {"fps-readout", "toggle-sprint", "crosshair", "hit-indicator",
                "freelook", "snaplook", "ping-readout", "hit-colour", "cosmetics", "settings-screen"}) {
            if (!reported.contains("\"id\": \"" + feature + "\"") || !reported.matches(
                    "(?s).*\\{ \"id\": \"" + feature + "\", \"name\": \"[^\"]*\", \"status\": \"loaded\" }.*")) {
                fail("the load report doesn't say " + feature + " loaded: " + reported);
            }
        }
    }

    /**
     * The list ash's launcher writes, hidden entry and all, read by the game's
     * own code: every name and address, in order, an icon kept, and the
     * hidden entry still hidden. servers.dat is the same on 1.20.4 as on the
     * 1.21.11 the fixture came from.
     */
    private static void serverListAshWroteLoads(Minecraft client) {
        Path written = Path.of(System.getProperty("ash.test.serversChangedByAsh"));
        List<String> seen = onClient(client, () -> {
            Path list = client.gameDirectory.toPath().resolve("servers.dat");
            Files.copy(written, list, StandardCopyOption.REPLACE_EXISTING);
            ServerList servers = new ServerList(client);
            servers.load();
            List<String> entries = new ArrayList<>();
            for (int i = 0; i < servers.size(); i++) {
                ServerData server = servers.get(i);
                entries.add(server.name + " | " + server.ip + " | icon " + (server.getIconBytes() != null));
            }
            entries.add("hidden kept: " + (servers.get("hidden.example.com") != null));
            Files.delete(list);
            return entries;
        });
        List<String> wanted = List.of(
                "ash ★ 😀 test | play.example.net:25570 | icon false",
                "Hypixel | mc.hypixel.net | icon true",
                "Local, renamed | localhost:25571 | icon false",
                "hidden kept: true");
        if (!wanted.equals(seen)) {
            fail("the game read the server list ash wrote as " + seen + ", not " + wanted);
        }
    }

    /**
     * The game view (spec 0007) answers from the game itself: no server in a
     * singleplayer world, chat open and shut, and F3 on and off.
     */
    private static void gameViewSees(Minecraft client) {
        GameView game = MinecraftGameView.INSTANCE;
        if (onClient(client, game::serverAddress) != null) {
            fail("the game view says a singleplayer world is a server");
        }

        onClient(client, () -> {
            client.setScreen(new ChatScreen(""));
            return null;
        });
        boolean chatSeen = onClient(client, game::chatOpen);
        onClient(client, () -> {
            client.setScreen(null);
            return null;
        });
        if (!chatSeen || onClient(client, game::chatOpen)) {
            fail("the game view did not follow chat opening and closing");
        }

        onClient(client, () -> {
            client.getDebugOverlay().toggleOverlay();
            return null;
        });
        boolean debugSeen = onClient(client, game::debugScreenShown);
        onClient(client, () -> {
            client.getDebugOverlay().toggleOverlay();
            return null;
        });
        if (!debugSeen || onClient(client, game::debugScreenShown)) {
            fail("the game view did not follow F3 opening and closing");
        }
    }

    /**
     * A HUD item drawn in a style the player chose (spec 0007): the frame
     * rate half as big again, in a box, cycling through the rainbow,
     * bracketed, with its label first. Judged by eye in the screenshot; the
     * style's arithmetic is HudItemStyleTest's. Put back as it was after.
     */
    private static void hudStyleShows(Minecraft client) {
        HudItemSettings style = Settings.FPS_READOUT_ITEM;
        onClient(client, () -> {
            AshClient.settings.set(style.size, 150);
            AshClient.settings.set(style.background, true);
            AshClient.settings.set(style.chroma, true);
            AshClient.settings.set(style.brackets, true);
            AshClient.settings.set(style.label, "before");
            return null;
        });
        pause(1_000L);
        screenshot(client, "ash-hud-styled.png");
        onClient(client, () -> {
            style.all().forEach(option -> reset(AshClient.settings, option));
            return null;
        });
    }

    private static <T> void reset(Settings settings, Setting<T> option) {
        settings.set(option, option.fallback());
    }

    /** ash's settings, opened with its own key as a player would, drawn, and closed again. */
    private static void settingsScreenOpensWithItsKey(Minecraft client) {
        KeyMapping key = onClient(client, () -> {
            for (KeyMapping mapping : client.options.keyMappings) {
                if (mapping.getName().equals(SettingsScreen.BINDING_NAME)) {
                    return mapping;
                }
            }
            return null;
        });
        if (key == null) {
            fail("ash's settings key isn't among the game's key bindings");
        }
        onClient(client, () -> {
            KeyMapping.click(key.getDefaultKey());
            return null;
        });
        await("open ash's settings with its key", () -> client.screen instanceof AshSettingsScreen ? Boolean.TRUE : null);
        pause(1_000L);
        screenshot(client, "ash-settings.png");
        searchWorks(client);
        snappingWorks(client);
        onClient(client, () -> {
            client.setScreen(null);
            return null;
        });
    }

    /** Whether a saved placement is flush against the right edge: a right-hand anchor, no gap. */
    private static boolean flushRight(String saved) {
        return saved.matches("(top-|bottom-)?right 0 -?\d+");
    }

    /**
     * Edit HUD's snapping, in the running game (spec 0007): the frame rate,
     * dragged until its right edge is two GUI units short of the screen's,
     * lies flush against it and is saved so: right-anchored, with no gap. Driven
     * through the panel as the screen hands it the mouse, in real pixels.
     */
    private static void snappingWorks(Minecraft client) {
        AshSettingsScreen screen = (AshSettingsScreen) client.screen;
        onClient(client, () -> {
            Rect edit = screen.panel().editHudButton();
            screen.panel().mouseClicked(edit.centreX(), edit.centreY());
            return null;
        });
        // A frame or two for the readouts, drawn beneath, to say how big they are.
        pause(500L);
        String unsnapped = onClient(client, () -> snapped(screen.panel(), (int) client.getWindow().getGuiScale(),
                client.getWindow().getGuiScaledWidth()));
        if (unsnapped != null) {
            fail(unsnapped);
        }
        screenshot(client, "ash-edit-hud-snapped.png");
        onClient(client, () -> {
            Rect reset = screen.panel().resetReadoutsButton();
            screen.panel().mouseClicked(reset.centreX(), reset.centreY());
            screen.panel().mouseClicked(reset.centreX(), reset.centreY());
            Rect done = screen.panel().doneButton();
            screen.panel().mouseClicked(done.centreX(), done.centreY());
            return null;
        });
    }

    private static String snapped(Panel panel, int scale, int guiWidth) {
        Rect box = panel.itemBox(HudItem.FPS);
        if (box == null) {
            return "the FPS readout has no box in Edit HUD";
        }
        int left = box.x / scale + 1;
        int width = box.width / scale - 1;
        int move = (guiWidth - width - 2 - left) * scale;
        panel.mouseClicked(box.centreX(), box.centreY());
        panel.mouseDragged(box.centreX() + move, box.centreY());
        panel.mouseReleased();
        String saved = AshClient.settings.get(Settings.FPS_READOUT_POSITION).format();
        return flushRight(saved) ? null : "dragged two short of the right edge, it was saved at " + saved;
    }

    /**
     * Search, typed into the open panel as the game hands typing to a screen
     * (spec 0007): an option's name finds its feature's tile and no other.
     * This run is a test build, so options are searched.
     */
    private static void searchWorks(Minecraft client) {
        AshSettingsScreen screen = (AshSettingsScreen) client.screen;
        onClient(client, () -> {
            for (char typed : "gap".toCharArray()) {
                screen.charTyped(typed, 0);
            }
            return null;
        });
        String query = onClient(client, () -> screen.panel().query());
        boolean crosshairShown = onClient(client, () -> screen.panel().switchOf(Feature.CROSSHAIR) != null);
        boolean fpsShown = onClient(client, () -> screen.panel().switchOf(Feature.FPS_READOUT) != null);
        if (!"gap".equals(query) || !crosshairShown || fpsShown) {
            fail("typing \"gap\" searched for \"" + query + "\" and showed the crosshair " + crosshairShown
                    + ", the FPS readout " + fpsShown);
        }
        screenshot(client, "ash-settings-search.png");
    }

    /** The player's ash cape, from the test's CDN, in the skin the game draws them with. */
    private static void capeWorks(Minecraft client) {
        await("put ash's cape from " + TestCdn.CAPE + " on the player", () -> {
            var cape = client.player.getSkin().capeTexture();
            return cape != null && cape.getNamespace().equals("ash") ? Boolean.TRUE : null;
        });
        onClient(client, () -> {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            return null;
        });
        pause(1_000L);
        screenshot(client, "ash-cape.png");
        onClient(client, () -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            return null;
        });
    }

    // ---- waiting on the game ----------------------------------------------------

    private interface Check<T> {
        T answer();
    }

    private static <T> T await(String what, Check<T> check) {
        long deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            T answer = check.answer();
            if (answer != null) {
                return answer;
            }
            pause(POLL_MS);
        }
        Minecraft c = Minecraft.getInstance();
        fail("the client did not " + what + " within " + STEP_TIMEOUT_MS / 1000 + " seconds; screen "
                + (c == null || c.screen == null ? "none" : c.screen.getClass().getSimpleName())
                + ", player " + (c != null && c.player != null));
        return null;
    }

    private static <T> T onClient(Minecraft client, Callable<T> work) {
        try {
            return client.submit(() -> {
                try {
                    return work.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting on the client thread");
        } catch (ExecutionException | TimeoutException failed) {
            fail("on the client thread: " + failed);
        }
        return null;
    }

    /** Into the run directory's {@code screenshots}, which CI keeps. */
    private static void screenshot(Minecraft client, String name) {
        onClient(client, () -> {
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> { });
            return null;
        });
    }

    private static void fail(String why) {
        System.err.println("ash smoke test failed: " + why);
        // `halt`, not `exit`: the exit code is all CI reads, and a shutdown
        // hook on a game that's already wrong can hang instead of failing.
        Runtime.getRuntime().halt(1);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the client");
        }
    }
}
