package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.sprint.ToggleKey;
import net.minecraft.client.option.KeyBinding;

/**
 * 1.8.9's answer to {@link ToggleKey}: the {@code KeyBinding} the player sees
 * in Controls.
 *
 * <p>Legacy Yarn's names read backwards from what they suggest, so the
 * bytecode was checked rather than the names trusted: {@code isPressed()}
 * returns the {@code pressed} field and means <em>held</em>, and
 * {@code wasPressed()} decrements {@code timesPressed} and <em>consumes one
 * press</em>.
 */
final class KeyBindingToggleKey implements ToggleKey {

    private final KeyBinding binding;

    KeyBindingToggleKey(KeyBinding binding) {
        this.binding = binding;
    }

    @Override
    public boolean held() {
        return binding.isPressed();
    }

    @Override
    public int takePresses() {
        int presses = 0;
        while (binding.wasPressed()) {
            presses++;
        }
        return presses;
    }
}
