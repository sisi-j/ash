package com.ashlauncher.client.v1_20_4.mixin;

import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggle sprint on 1.20.4: where the game asks whether the sprint key is down.
 *
 * <p>Not {@code KeyboardInput}, as on 1.21.11. 1.20.4's {@code
 * KeyboardInput.tick} reads movement, jump and sneak only; the sprint key is
 * read in {@code LocalPlayer.aiStep()}, twice (research 0015, checked in the
 * bytecode). So this wraps every {@code isDown()} there and changes only the
 * ones whose receiver <em>is</em> the sprint key, rather than counting calls:
 * a reordering of the others then changes nothing.
 *
 * <p>Nothing else is touched. The game goes on to decide whether to sprint -
 * hunger, blindness, moving forward - and to tell the server, as it does for
 * the sprint key itself. While a screen is open the latch reads as released,
 * as the game's own keys are.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerSprintMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void ash$readToggleKey(CallbackInfo info) {
        // The player, for its identity: a new one after a death or a world
        // change starts with the latch off, as the game's own toggle keys do.
        ToggleSprintHook.tick((LocalPlayer) (Object) this);
    }

    @WrapOperation(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean ash$sprintKeyDown(KeyMapping key, Operation<Boolean> original) {
        boolean down = original.call(key);
        Minecraft minecraft = Minecraft.getInstance();
        if (key != minecraft.options.keySprint) {
            return down;
        }
        return ToggleSprintHook.sprintKeyDown(down, minecraft.screen != null);
    }
}
