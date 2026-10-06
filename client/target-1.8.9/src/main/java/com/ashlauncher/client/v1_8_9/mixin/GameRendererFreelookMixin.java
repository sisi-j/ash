package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.freelook.FreelookHook;
import com.ashlauncher.client.v1_8_9.FreelookView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook on 1.8.9: the mouse, and the view.
 *
 * <p><b>The mouse.</b> {@code GameRenderer.render} turns the player with
 * {@code increaseTransforms} in two places - the smooth-camera branch and the
 * other ({@code docs/research/0004}, section 5). While freelook is held both go
 * to freelook's camera instead. 1.8.9 subtracts its pitch where 1.21.11 adds
 * it, so the pitch is handed over negated: the mouse turns the same way on
 * both targets.
 *
 * <p><b>The view.</b> 1.8.9 has no camera object. The view transform, the
 * check of which chunks are visible, and particle facing all read the camera
 * entity's own {@code yaw} and {@code pitch} fields. So for the length of the
 * world's drawing those fields hold freelook's angles, and they are put back
 * the moment it ends. That starts after {@code updateTargetedEntity}, so what
 * a click hits stays where the player faces, and it never spans a tick, where
 * movement packets are sent - the server is never told the camera's angles.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererFreelookMixin {

    @WrapOperation(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ClientPlayerEntity;increaseTransforms(FF)V"))
    private void ash$freelookTurn(ClientPlayerEntity player, float yaw, float pitch, Operation<Void> original) {
        if (!FreelookHook.turn(yaw, -pitch)) {
            original.call(player, yaw, pitch);
        }
    }

    @Inject(method = "renderWorld(FJ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;updateTargetedEntity(F)V",
                    shift = At.Shift.AFTER))
    private void ash$freelookView(float tickDelta, long limit, CallbackInfo info) {
        FreelookView.turnTo();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("RETURN"))
    private void ash$playersView(float tickDelta, long limit, CallbackInfo info) {
        FreelookView.turnBack();
    }
}
