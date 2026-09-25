package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle sprint on 1.8.9: where the local player asks whether the sprint key
 * is down.
 *
 * <p>{@code ClientPlayerEntity.tickMovement()} is the only reader of
 * {@code GameOptions.sprintKey} in the game - established by scanning every
 * class, see {@code docs/research/0003-vanilla-names-for-toggle-sprint.md} -
 * and it reads it <em>twice</em>: once in the double-tap-forward branch and
 * once in the held-key branch. Catching only the second would still start a
 * sprint, but the first would go on arming the double-tap timer for a key
 * that is "held", which holding the key never does. So both are answered, by
 * wrapping every {@code isPressed()} in the method and changing only the one
 * whose receiver is the sprint key.
 *
 * <p>The class is in {@code net.minecraft.entity.player} in Legacy Yarn, not
 * {@code net.minecraft.client.network} where modern Yarn has it - the first
 * guess, and wrong.
 */
@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin {

    @Shadow
    protected MinecraftClient client;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void ash$readToggleKey(CallbackInfo info) {
        // This player, for its identity: a new one after a death or a world
        // change starts with the latch off.
        ToggleSprintHook.tick(this);
    }

    @WrapOperation(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean ash$sprintKeyDown(KeyBinding key, Operation<Boolean> original) {
        boolean down = original.call(key);
        if (key != client.options.sprintKey) {
            return down;
        }
        // Released with every other key while a screen is open, as the game
        // releases them - the same rule as on 1.21.11, where it is also what
        // keeps "sprint held" out of the packets a menu sends.
        return ToggleSprintHook.sprintKeyDown(down, client.currentScreen != null);
    }
}
