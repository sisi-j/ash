package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.bench.Benchmark;
import com.ashlauncher.client.bench.Result;
import com.ashlauncher.client.bench.Runs;
import com.ashlauncher.client.bench.Scene;
import com.mojang.blaze3d.platform.GLX;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * The frame-time measurement on 1.8.9 (#42): the fixed scene in
 * {@code bench.Scene}, run in a real game, with every frame's time recorded
 * and a profile of where the frame goes.
 *
 * <p>Run by hand on a real machine, with {@code ./gradlew
 * :target-1.8.9:runBenchmark}; see {@code client/README.md}. Never in a
 * player's game: this source set is not in the jar.
 *
 * <p>Everything a run decides - the warm-up, the passes, which frames
 * count - is {@link Benchmark}'s, tested without a game. This sets the game up
 * for the scene, tells the run each frame's time from the HUD's callback,
 * which fires once a frame, turns the camera to where the run says, and
 * writes what it found.
 */
public final class AshBenchmark implements ClientModInitializer {

    /** After the passes, with the game's profiler on: long enough for its averages to settle. */
    private static final int PROFILE_SECONDS = 15;

    /** The world's folder, deleted and made afresh each run, so every run generates the same terrain. */
    private static final String WORLD = "ash-benchmark";

    /** The game's own "unlimited" frame rate. */
    private static final int UNLIMITED = 260;

    private static volatile Benchmark run;
    private static int announcedPass;
    private static List<Result.Section> profile = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((client, tickDelta) -> frame(client));
        // The game is still starting; a world can only be started once it
        // has a screen up, so a thread waits for that rather than blocking
        // the game.
        Thread starter = new Thread(AshBenchmark::start, "ash-benchmark");
        starter.setDaemon(true);
        starter.start();
    }

    private static void start() {
        MinecraftClient client = waitFor("the title screen", () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            return c != null && c.currentScreen != null ? c : null;
        });
        client.submit(() -> {
            GameOptions options = client.options;
            options.pauseOnLostFocus = false;
            options.difficulty = Difficulty.PEACEFUL;
            options.viewDistance = Scene.RENDER_DISTANCE;
            options.maxFramerate = UNLIMITED;
            options.vsync = false;
            // The field alone is only read when the menu changes it; the
            // first trial run sat at the screen's 144 Hz, waiting on vsync
            // in `display_update` for 59% of every frame.
            Display.setVSyncEnabled(false);
            options.guiScale = Scene.GUI_SCALE;
            options.fullscreen = false;
            options.save();
            client.getCurrentSave().deleteLevel(WORLD);
            client.startIntegratedServer(WORLD, WORLD,
                    new LevelInfo(Scene.SEED, LevelInfo.GameMode.SPECTATOR, true, false, LevelGeneratorType.DEFAULT));
        });
        waitFor("the world", () -> client.world != null && client.player != null && client.currentScreen == null
                && client.getServer() != null ? client : null);

        IntegratedServer server = client.getServer();
        server.submit(() -> {
            String[] commands = {
                "gamerule doDaylightCycle false",
                "gamerule doMobSpawning false",
                "time set " + Scene.TIME_OF_DAY,
                "weather clear 1000000",
                "tp @a " + Scene.X + " " + Scene.Y + " " + Scene.Z + " 0 " + Scene.PITCH,
            };
            for (String command : commands) {
                server.getCommandManager().execute(server, "/" + command);
            }
        });
        say("in the world; warming up for " + Scene.WARM_UP_SECONDS + " s, then " + Scene.PASSES + " passes of "
                + Scene.PASS_SECONDS + " s and a " + PROFILE_SECONDS + " s profile. Leave the window alone.");
        run = Benchmark.ofScene(PROFILE_SECONDS);
    }

    /** Once a frame, on the render thread, from the HUD's callback. */
    private static void frame(MinecraftClient client) {
        Benchmark current = run;
        if (current == null || current.phase() == Benchmark.Phase.DONE || client.player == null) {
            return;
        }
        long now = System.nanoTime();
        Benchmark.Phase phase = current.frame(now);

        // The camera, by the clock: prev as well, so the game does not
        // interpolate back towards where the last tick had it.
        float yaw = current.yaw(now);
        client.player.yaw = yaw;
        client.player.prevYaw = yaw;
        client.player.pitch = Scene.PITCH;
        client.player.prevPitch = Scene.PITCH;

        if (phase == Benchmark.Phase.MEASURING && current.pass() != announcedPass) {
            announcedPass = current.pass();
            if (announcedPass == 1) {
                say(String.format(java.util.Locale.ROOT, "warmed up in %.0f s", current.warmedUpSeconds()));
            }
            say("pass " + announcedPass + " of " + current.passes());
        } else if (phase == Benchmark.Phase.PROFILING && !client.options.debugProfilerEnabled) {
            // The game profiles only while its debug screen shows the
            // profiler's chart, and drawing that costs time - which is why
            // these frames do not count.
            client.options.debugEnabled = true;
            client.options.debugProfilerEnabled = true;
            say("profiling");
        } else if (phase == Benchmark.Phase.DONE) {
            finish(client, current);
        }
    }

    private static void finish(MinecraftClient client, Benchmark current) {
        // Read while the profiler is still on: it answers nothing once off.
        if (client.profiler.enabled) {
            collect(client.profiler, "root", 0, profile);
        }
        client.options.debugEnabled = false;
        client.options.debugProfilerEnabled = false;

        long nowMillis = System.currentTimeMillis();
        Result result = new Result("1.8.9", Runs.label(), Runs.instant(nowMillis),
                Runs.machine(GLX.getProcessor(), GL11.glGetString(GL11.GL_RENDERER),
                        GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_VERSION)),
                Runs.ashSettings(FabricLoader.getInstance().getConfigDir()), current.measured(),
                current.warmedUpSeconds(), current.settledAtStart(), profile);
        try {
            Path written = result.write(Runs.outputDirectory(client.runDirectory.toPath()), Runs.stamp(nowMillis));
            say(result.oneLine());
            say("written to " + written.toAbsolutePath());
        } catch (IOException unwritable) {
            say("could not write the result (" + unwritable + "): " + result.oneLine());
        }
        client.scheduleStop();
    }

    /**
     * The game's profile, three sections deep, keeping each section worth at
     * least 1% of a frame. Its first answer for a path is the path itself,
     * which is skipped; "unspecified" is time in a section outside any of
     * its children.
     */
    private static void collect(Profiler profiler, String path, int depth, List<Result.Section> into) {
        List<Profiler.Section> sections = profiler.getData(path);
        if (sections == null || depth >= 3) {
            return;
        }
        for (int i = 1; i < sections.size(); i++) {
            Profiler.Section section = sections.get(i);
            if (section.absolutePercentage < 1.0) {
                continue;
            }
            String child = path + "." + section.name;
            into.add(new Result.Section(child, section.absolutePercentage));
            if (!"unspecified".equals(section.name)) {
                collect(profiler, child, depth + 1, into);
            }
        }
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
