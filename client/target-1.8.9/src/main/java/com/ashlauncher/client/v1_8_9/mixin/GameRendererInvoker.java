package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** THROWAWAY spike for research 0007: the game's own private "load this post shader". */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    @Invoker("loadShader")
    void ash$loadShader(Identifier id);
}
