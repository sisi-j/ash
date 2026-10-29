package com.ashlauncher.client.v26_3.mixin;

import com.ashlauncher.client.freelook.FreelookHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freelook's camera on 26.3: where the camera takes its angles.
 *
 * <p>{@code Camera.alignWithEntity} - {@code setup}'s part on 1.21.11, which
 * 26.3's {@code Camera.update} calls before it builds the culling frustum -
 * reads the entity's view yaw and pitch, in its minecart branch and
 * otherwise, and sets its rotation from them ({@code docs/research/0016},
 * section 2). The front
 * view's mirror and the third-person pull-back both work from that rotation
 * ({@code docs/research/0009}, section 5). So answering these two reads with
 * freelook's angles turns the view, and the third-person orbit follows. The
 * entity's own rotation is read, never written.
 */
@Mixin(Camera.class)
abstract class CameraMixin {

    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float ash$freelookYaw(Entity entity, float partialTick, Operation<Float> original) {
        return FreelookHook.yaw(original.call(entity, partialTick));
    }

    @WrapOperation(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float ash$freelookPitch(Entity entity, float partialTick, Operation<Float> original) {
        return FreelookHook.pitch(original.call(entity, partialTick));
    }
}
