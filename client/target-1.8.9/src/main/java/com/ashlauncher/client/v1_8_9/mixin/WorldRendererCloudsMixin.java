package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.FasterClouds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faster clouds on 1.8.9: the game's fancy clouds, handed to
 * {@link FasterClouds} at the top of {@code renderFancyClouds} when the
 * player has it on. The game decides whether clouds are fancy at all - the
 * clouds option, the render distance, the dimension - before it gets here,
 * so all of that stays the game's.
 *
 * <p>If this does not land, the game draws its own clouds, as it always has.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererCloudsMixin {

    @Shadow
    @Final
    private static Identifier CLOUDS;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Shadow
    private ClientWorld world;

    @Shadow
    private int ticks;

    @Inject(method = "renderFancyClouds", at = @At("HEAD"), cancellable = true)
    private void ash$fasterClouds(float tickDelta, int anaglyphFilter, CallbackInfo info) {
        if (FasterClouds.on()) {
            FasterClouds.render(client, world, ticks, textureManager, CLOUDS, tickDelta, anaglyphFilter);
            info.cancel();
        }
    }
}
