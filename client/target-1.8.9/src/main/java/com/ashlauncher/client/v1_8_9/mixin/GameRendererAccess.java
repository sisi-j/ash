package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The game's post-shader slot, which ash's settings panel borrows for its
 * blur: it runs after the world and before the HUD and any screen, so the
 * world blurs and the panel does not (`docs/research/0007`).
 *
 * <p>Loading a shader also switches post shaders on, so the panel reads the
 * switch before borrowing the slot and sets it back afterwards: a shader the
 * player had switched off stays off.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {

    @Invoker("loadShader")
    void ash$loadShader(Identifier id);

    @Accessor("shadersEnabled")
    boolean ash$shadersEnabled();

    @Accessor("shadersEnabled")
    void ash$setShadersEnabled(boolean enabled);
}
