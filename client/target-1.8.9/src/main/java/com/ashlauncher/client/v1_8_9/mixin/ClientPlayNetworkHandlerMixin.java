package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.hit.HitHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The hit indicator on 1.8.9, half two: the server says an entity was hurt.
 *
 * <p>Status 2, sent only on a fresh hit, and naming the entity but not who
 * hit it - so {@code HitHook} matches it to the player's own recent attack.
 * At the handler's hand-off to the entity rather than at its head: the
 * handler is first called on the network thread, where it schedules itself
 * onto the game thread and throws, so a head injection would see every packet
 * twice. And on the handler rather than on {@code handleStatus}, which many
 * entities override. See
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 2.3.
 */
@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {

    /** "Hurt", in 1.8.9's entity statuses. */
    private static final byte HURT = 2;

    @WrapOperation(method = "onEntityStatus", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;handleStatus(B)V"))
    private void ash$hurt(Entity entity, byte status, Operation<Void> original) {
        original.call(entity, status);
        if (status == HURT) {
            HitHook.hurt(entity.getEntityId());
        }
    }
}
