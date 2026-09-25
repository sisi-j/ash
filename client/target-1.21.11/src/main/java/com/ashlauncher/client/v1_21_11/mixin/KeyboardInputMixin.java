package com.ashlauncher.client.v1_21_11.mixin;

import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle sprint on 1.21.11: the one place the game asks whether the sprint key
 * is down.
 *
 * <p>{@code KeyboardInput.tick()} is the only reader of {@code Options.keySprint}
 * in the client - established by scanning every class, see
 * {@code docs/research/0003-vanilla-names-for-toggle-sprint.md} - and it reads
 * it as the seventh of seven {@code isDown()} calls that build the tick's
 * {@code Input}. So this wraps every {@code isDown()} there and changes only
 * the one whose receiver <em>is</em> the sprint key, rather than counting to
 * seven: a reordering of the others then changes nothing.
 *
 * <p>Nothing else is touched. The game goes on to decide whether to sprint -
 * hunger, blindness, moving forward - and to tell the server. What it tells
 * the server includes this very answer: since 1.21.2 the player's keys are
 * sent every tick, menu or not. So the latch is asked whether a screen is
 * open, where the game has released every key, and reads as released there
 * too - otherwise ash would send "sprint held" from inside an inventory, which
 * no vanilla client can.
 */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {

    @Shadow
    @Final
    private Options options;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ash$readToggleKey(CallbackInfo info) {
        // The player, for its identity: a new one after a death or a world
        // change starts with the latch off, as the game's own toggle keys do.
        ToggleSprintHook.tick(Minecraft.getInstance().player);
    }

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDownInAFutureVersion()Z"))
    private boolean ash$sprintKeyDown(KeyMapping key, Operation<Boolean> original) {
        boolean down = original.call(key);
        if (key != options.keySprint) {
            return down;
        }
        return ToggleSprintHook.sprintKeyDown(down, Minecraft.getInstance().screen != null);
    }
}
