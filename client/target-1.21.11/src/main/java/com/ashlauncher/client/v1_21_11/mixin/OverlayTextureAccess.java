package com.ashlauncher.client.v1_21_11.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Hit colour on 1.21.11: the game's overlay texture, whose red rows every
 * hurt entity's flash samples. Its texture is private, so this is the one way
 * to rewrite those pixels and upload them again ({@code docs/research/0004},
 * section 4.1).
 */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccess {

    @Accessor("texture")
    DynamicTexture ash$texture();
}
