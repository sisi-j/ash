package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Opens ash's settings screen when its key is pressed, called once a client
 * tick by {@code MinecraftClientMixin}.
 *
 * <p>Static because a mixin can only reach the rest of ash through something
 * static; installed by {@link AshClient} at startup, and does nothing before.
 */
public final class SettingsKey {

    private static KeyBinding key;
    private static SettingsScreen settingsScreen;

    private SettingsKey() {
    }

    static void install(KeyBinding key, SettingsScreen settingsScreen) {
        SettingsKey.key = key;
        SettingsKey.settingsScreen = settingsScreen;
    }

    /**
     * A binding gets no presses while a screen is open, so this only ever
     * opens the screen; the screen closes itself on the same key.
     */
    public static void tick() {
        if (key == null) {
            return;
        }
        while (key.wasPressed()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == null) {
                client.setScreen(new AshSettingsScreen(settingsScreen, key));
            }
        }
    }
}
