package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.snaplook.Snaplook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Snaplook's key on 1.8.9, read once a client tick by
 * {@code MinecraftClientSnaplookMixin}. Installed by {@link AshClient} at
 * startup; before that, this does nothing.
 */
public final class SnaplookKey {

    private static KeyBinding key;
    private static Snaplook<Integer> snaplook;

    private SnaplookKey() {
    }

    static void install(KeyBinding key, Snaplook<Integer> snaplook) {
        SnaplookKey.key = key;
        SnaplookKey.snaplook = snaplook;
    }

    /** The feature, for the smoke test to ask. */
    static Snaplook<Integer> snaplook() {
        return snaplook;
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (snaplook != null && client.player != null) {
            snaplook.tick(key.isPressed(), client.currentScreen != null);
        }
    }
}
