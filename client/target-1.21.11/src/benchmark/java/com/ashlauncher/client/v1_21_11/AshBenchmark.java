package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.bench.Benchmark;
import com.ashlauncher.client.bench.Result;
import com.ashlauncher.client.bench.Runs;
import com.ashlauncher.client.bench.Scene;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * The frame-time measurement on 1.21.11 (#42): the fixed scene in
 * {@code bench.Scene}, run in a real game, with every frame's time recorded.
 *
 * <p>Run by hand on a real machine, with {@code ./gradlew
 * :target-1.21.11:runBenchmark}; see {@code client/README.md}. Never in a
 * player's game: this source set is not in the jar.
 *
 * <p>Not Fabric's client game test framework, which the real-game test uses:
 * that steps the game a tick and a frame at a time, which is right for a
 * test and makes frame times mean nothing. A plain entrypoint, so the game
 * runs as a player's does.
 *
 * <p>No profile: the ticket's profile is 1.8.9's, where ash's own
 * optimisations will be. Everything a run decides is {@link Benchmark}'s.
 */
public final class AshBenchmark implements ClientModInitializer {

    private static final Identifier CLOCK = Identifier.fromNamespaceAndPath("ash-benchmark", "clock");

    /** The world's folder, deleted and made afresh each run, so every run generates the same terrain. */
    private static final String WORLD = "ash-benchmark";

    /** The game's own "unlimited" frame rate. */
    private static final int UNLIMITED = 260;

    private static volatile Benchmark run;
    private static int announcedPass;

    @Override
    public void onInitializeClient() {
        // Every frame, HUD hidden or not: `addLast` inherits no render condition.
        HudElementRegistry.addLast(CLOCK, (graphics, tickCounter) -> frame(Minecraft.getInstance()));
        Thread starter = new Thread(AshBenchmark::start, "ash-benchmark");
        starter.setDaemon(true);
        starter.start();
    }

    private static void start() {
        Minecraft client = waitFor("the title screen", () -> {
            Minecraft c = Minecraft.getInstance();
            return c != null && c.screen != null ? c : null;
        });
        client.execute(() -> {
            Options options = client.options;
            options.pauseOnLostFocus = false;
            // Not "when idle": with no input for a minute the game drops to
            // 30 frames a second, and a benchmark is nothing but no input.
            options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            options.renderDistance().set(Scene.RENDER_DISTANCE);
            options.framerateLimit().set(UNLIMITED);
            options.enableVsync().set(false);
            options.guiScale().set(Scene.GUI_SCALE);
            options.fullscreen().set(false);
            options.save();
            try {
                LevelStorageSource levels = client.getLevelSource();
                if (levels.levelExists(WORLD)) {
                    try (LevelStorageSource.LevelStorageAccess access = levels.createAccess(WORLD)) {
                        access.deleteLevel();
                    }
                }
            } catch (IOException undeletable) {
                throw new IllegalStateException("ash benchmark: could not delete the last run's world", undeletable);
            }
            WorldDataConfiguration data = WorldDataConfiguration.DEFAULT;
            client.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true,
                            new GameRules(data.enabledFeatures()), data),
                    new WorldOptions(Scene.SEED, true, false), WorldPresets::createNormalWorldDimensions, client.screen);
        });
        waitFor("the world", () -> client.level != null && client.player != null && client.screen == null
                && client.getSingleplayerServer() != null ? client : null);

        IntegratedServer server = client.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel overworld = server.overworld();
            GameRules rules = overworld.getGameRules();
            rules.set(GameRules.ADVANCE_TIME, false, server);
            rules.set(GameRules.ADVANCE_WEATHER, false, server);
            rules.set(GameRules.SPAWN_MOBS, false, server);
            overworld.setDayTime(Scene.TIME_OF_DAY);
            overworld.setWeatherParameters(1_000_000, 0, false, false);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "tp @a " + Scene.X + " " + Scene.Y + " " + Scene.Z + " 0 " + Scene.PITCH);
        });
        say("in the world; warming up for at least " + Scene.WARM_UP_SECONDS + " s, until the world settles, then " + Scene.PASSES + " passes of "
                + Scene.PASS_SECONDS + " s. Leave the window alone.");
        run = Benchmark.ofScene(0);
    }

    /** Once a frame, on the render thread, from the HUD. */
    private static void frame(Minecraft client) {
        Benchmark current = run;
        if (current == null || current.phase() == Benchmark.Phase.DONE || client.player == null) {
            return;
        }
        long now = System.nanoTime();
        Benchmark.Phase phase = current.frame(now, settled(client));

        // The camera, by the clock: the old rotation as well, so the game
        // does not interpolate back towards where the last tick had it.
        float yaw = current.yaw(now);
        client.player.setYRot(yaw);
        client.player.yRotO = yaw;
        client.player.setXRot(Scene.PITCH);
        client.player.xRotO = Scene.PITCH;

        if (phase == Benchmark.Phase.MEASURING && current.pass() != announcedPass) {
            announcedPass = current.pass();
            if (announcedPass == 1) {
                say(String.format(java.util.Locale.ROOT, "warmed up in %.0f s", current.warmedUpSeconds()));
            }
            say("pass " + announcedPass + " of " + current.passes());
        } else if (phase == Benchmark.Phase.DONE) {
            finish(client, current);
        }
    }

    /**
     * Whether the world has finished being made round the camera: the
     * server has no chunk work waiting, and every section in view is built.
     * A first trial without this measured its passes at 511, 607 and 661
     * frames a second as the server went on generating.
     */
    private static boolean settled(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        return server != null && server.overworld().getChunkSource().getPendingTasksCount() == 0
                && client.levelRenderer.hasRenderedAllSections();
    }

    private static void finish(Minecraft client, Benchmark current) {
        GpuDevice device = RenderSystem.getDevice();
        long nowMillis = System.currentTimeMillis();
        Result result = new Result("1.21.11", Runs.label(), Runs.instant(nowMillis),
                Runs.machine(GLX._getCpuInfo(), device.getRenderer(), device.getVendor(), device.getVersion()),
                Runs.ashSettings(FabricLoader.getInstance().getConfigDir()), current.measured(),
                current.warmedUpSeconds(), current.settledAtStart(),
                Collections.<Result.Section>emptyList());
        try {
            Path written = result.write(Runs.outputDirectory(client.gameDirectory.toPath()), Runs.stamp(nowMillis));
            say(result.oneLine());
            say("written to " + written.toAbsolutePath());
            // How much of the world it drew: the first thing to compare when two
            // runs of the same build disagree (see benchmark-results, FPS marks).
            say("drew " + client.levelRenderer.countRenderedSections() + " sections; "
                    + client.levelRenderer.getSectionStatistics());
        } catch (IOException unwritable) {
            say("could not write the result (" + unwritable + "): " + result.oneLine());
        }
        client.stop();
    }

    private static void say(String line) {
        System.out.println("ash benchmark: " + line);
    }

    private interface Check<T> {
        T answer();
    }

    /** Polls until {@code check} answers, for as long as a slow machine could need. */
    private static <T> T waitFor(String what, Check<T> check) {
        long deadline = System.currentTimeMillis() + 5 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            T answer = check.answer();
            if (answer != null) {
                return answer;
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("ash benchmark: gave up waiting for " + what);
    }
}
