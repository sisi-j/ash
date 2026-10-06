package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.freelook.CameraModes;
import com.ashlauncher.client.freelook.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Freelook's key on 1.8.9, read once a client tick by
 * {@code MinecraftClientFreelookMixin}. A mixin can only call out statically,
 * so the feature is installed here by {@link AshClient} at startup; before
 * that, this does nothing.
 */
public final class FreelookKey {

    private static KeyBinding key;
    private static Freelook<Integer> freelook;

    private FreelookKey() {
    }

    static void install(KeyBinding key, Freelook<Integer> freelook) {
        FreelookKey.key = key;
        FreelookKey.freelook = freelook;
    }

    /** The feature, for the smoke test to ask. */
    static Freelook<Integer> freelook() {
        return freelook;
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (freelook != null && client.player != null) {
            freelook.tick(key.isPressed(), client.currentScreen != null, client.player.yaw, client.player.pitch);
        }
    }

    /** The game's own camera modes on 1.8.9: {@code GameOptions.perspective}, 0 first person, 1 behind, 2 in front. */
    static final class Perspective implements CameraModes<Integer> {

        @Override
        public Integer current() {
            return MinecraftClient.getInstance().options.perspective;
        }

        @Override
        public void set(Integer mode) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.options.perspective = mode;
            // As the game's own F5 does: the view moved, so which chunks show may have too.
            client.worldRenderer.scheduleTerrainUpdate();
        }

        @Override
        public boolean isFirstPerson(Integer mode) {
            return mode == 0;
        }

        @Override
        public Integer behind() {
            return 1;
        }
    }
}
