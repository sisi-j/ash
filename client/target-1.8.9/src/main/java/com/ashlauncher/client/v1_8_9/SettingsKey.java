package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsMenu;
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
    private static SettingsMenu menu;

    private SettingsKey() {
    }

    static void install(KeyBinding key, SettingsMenu menu) {
        SettingsKey.key = key;
        SettingsKey.menu = menu;
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
                client.setScreen(new AshSettingsScreen(menu, key));
            }
        }
    }
}
