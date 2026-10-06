package com.ashlauncher.client.v1_21_11.mixin;

import com.ashlauncher.client.freelook.FreelookHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freelook's mouse on 1.21.11: the one call that turns the player.
 *
 * <p>{@code turnPlayer} works out the turn from sensitivity, smoothing and
 * inversion, then hands it to the player in a single {@code LocalPlayer.turn}
 * ({@code docs/research/0009}, section 5). While freelook is held the turn goes
 * to freelook's camera instead, and the player - whose rotation is what the
 * server is sent - is never written.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @WrapOperation(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void ash$freelookTurn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (!FreelookHook.turn(dx, dy)) {
            original.call(player, dx, dy);
        }
    }
}
