package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.AshSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No HUD under ash's settings panel on 1.8.9, so the two targets look the
 * same. 1.21.11 blurs its HUD with the world; 1.8.9's blur runs before the
 * HUD is drawn, so its hotbar, hearts and crosshair would stay sharp under
 * the panel. This draws none of them while the panel is open
 * (`docs/research/0007`, and the spec's decision of 2026-10-03).
 *
 * <p>If it ever stops matching, the HUD simply shows under the panel again.
 */
@Mixin(InGameHud.class)
abstract class InGameHudPanelMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ash$hideUnderPanel(float tickDelta, CallbackInfo info) {
        if (MinecraftClient.getInstance().currentScreen instanceof AshSettingsScreen) {
            info.cancel();
        }
    }
}
