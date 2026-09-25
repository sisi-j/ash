package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.sprint.ToggleKey;
import net.minecraft.client.KeyMapping;

/**
 * 1.21.11's answer to {@link ToggleKey}: the {@code KeyMapping} the player
 * sees in Controls.
 *
 * <p>Read through the mapping rather than a key code, so a rebinding in
 * Controls takes effect on the next tick with nothing here knowing about it.
 */
final class KeyMappingToggleKey implements ToggleKey {

    private final KeyMapping mapping;

    KeyMappingToggleKey(KeyMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public boolean held() {
        return mapping.isDown();
    }

    @Override
    public int takePresses() {
        // Every press since the last tick, key repeats included - 1.21.11
        // counts a repeat as a click. The latch is what tells them apart.
        int presses = 0;
        while (mapping.consumeClick()) {
            presses++;
        }
        return presses;
    }
}
