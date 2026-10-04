package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The game's own private "load this post shader", which ash's settings panel
 * uses for its blur. The slot it loads into runs after the world and before
 * the HUD and any screen, so the world blurs and the panel does not
 * (`docs/research/0007`).
 */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("loadShader")
    void ash$loadShader(Identifier id);
}
