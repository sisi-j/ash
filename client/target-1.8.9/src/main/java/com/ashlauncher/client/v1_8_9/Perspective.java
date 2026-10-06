package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.freelook.CameraModes;
import net.minecraft.client.MinecraftClient;

/**
 * The game's own camera modes on 1.8.9: {@code GameOptions.perspective}, 0
 * first person, 1 behind, 2 in front. See {@code docs/research/0009}, 4.
 */
final class Perspective implements CameraModes<Integer> {

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
    public Integer front() {
        return 2;
    }

    @Override
    public Integer behind() {
        return 1;
    }
}
