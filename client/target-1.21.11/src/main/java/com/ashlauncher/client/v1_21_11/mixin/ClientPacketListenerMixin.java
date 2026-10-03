package com.ashlauncher.client.v1_21_11.mixin;

import com.ashlauncher.client.hit.HitHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hit indicator on 1.21.11: the server's damage event, which names who
 * caused the damage, so the client knows exactly which hits were the player's.
 *
 * <p>At the handler's hand-off to the entity, not at its head. The handler is
 * first called on the network thread, where it schedules itself onto the game
 * thread and throws; a head injection would see every packet twice. By the
 * hand-off it is on the game thread and the entity exists. Not entity event 2,
 * which on this version is a kinetic hit, and not the hurt-animation packet,
 * which only ever tells a player about themselves. Every name here was read
 * from the mapped jar: see
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 2.1.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {

    @Inject(method = "handleDamageEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void ash$damageEvent(ClientboundDamageEventPacket packet, CallbackInfo info) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            HitHook.damageEvent(packet.entityId(), packet.sourceCauseId(), player.getId());
        }
    }
}
