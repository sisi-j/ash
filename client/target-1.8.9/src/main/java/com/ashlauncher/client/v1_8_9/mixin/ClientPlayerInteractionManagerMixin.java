package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.hit.HitHook;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hit indicator on 1.8.9, half one: the player attacked an entity.
 *
 * <p>Only a click. 1.8.9's "this entity was hurt" names no attacker, so a
 * hurt is matched against these - see {@code ClientPlayNetworkHandlerMixin}.
 * A mixin because Legacy Fabric API has no attack event. Here rather than on
 * {@code PlayerEntity.attack}, whose success path runs on the click against
 * another player. {@code MinecraftClient.doAttack} is this method's only
 * caller. See {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md},
 * 2.3.
 */
@Mixin(ClientPlayerInteractionManager.class)
abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void ash$attacked(PlayerEntity player, Entity target, CallbackInfo info) {
        HitHook.attacked(target.getEntityId());
    }
}
