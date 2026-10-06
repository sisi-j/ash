package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.hit.HitColourHook;
import java.nio.FloatBuffer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hit colour on 1.8.9: the four floats the living-entity renderer passes as
 * its hurt flash, put in its buffer per draw.
 *
 * <p>{@code method_10252}, unnamed in Legacy Yarn 604 (MCP's
 * {@code setBrightness}), puts red, green, blue and 0.3 into {@code buffer}
 * when the entity is hurt or dying, and the creeper-style colour otherwise,
 * then flips the buffer once for {@code glTexEnv} ({@code docs/research/0004},
 * section 4.2). Just before that flip, a hurt entity's buffer is refilled with
 * ash's colour - read on every draw, so a change shows at once. Armour passes
 * {@code combineTextures} false and returns before this, so it never flashes,
 * as in the game itself.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererHitColourMixin {

    @Shadow
    protected FloatBuffer buffer;

    @Inject(method = "method_10252",
            at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;flip()Ljava/nio/Buffer;"))
    private void ash$hitColour(LivingEntity entity, float tickDelta, boolean combine,
            CallbackInfoReturnable<Boolean> info) {
        // The game checks hurt before the creeper colour, so a hurt entity's
        // buffer holds the hurt flash here.
        if (entity.hurtTime <= 0 && entity.deathTime <= 0) {
            return;
        }
        float[] colour = HitColourHook.envColour();
        if (colour == null) {
            return;
        }
        buffer.clear();
        buffer.put(colour[0]);
        buffer.put(colour[1]);
        buffer.put(colour[2]);
        buffer.put(colour[3]);
    }
}
