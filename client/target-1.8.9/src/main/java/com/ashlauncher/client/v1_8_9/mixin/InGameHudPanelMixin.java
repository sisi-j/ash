package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.AshSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No HUD under ash's settings panel on 1.8.9, so the two targets look the
 * same. 1.21.11 blurs its HUD with the world; 1.8.9's blur runs before the
 * HUD is drawn, so its hotbar, hearts and crosshair would stay sharp under
 * the panel. This draws none of them while the panel is open
 * (`docs/research/0007`, and the spec's decision of 2026-10-03). Except in
 * Edit HUD, which is there to show the HUD as it will be.
 *
 * <p>Right after the HUD sets up the 2D projection, not at its start: in a
 * world, that call is the only one that sets the projection every screen
 * then draws with, and skipping it draws the panel nowhere at all.
 *
 * <p>If it ever stops matching, the HUD simply shows under the panel again.
 */
@Mixin(InGameHud.class)
abstract class InGameHudPanelMixin {

    @Inject(method = "render", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;setupHudMatrixMode()V", shift = At.Shift.AFTER))
    private void ash$hideUnderPanel(float tickDelta, CallbackInfo info) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof AshSettingsScreen && ((AshSettingsScreen) screen).hidesHud()) {
            info.cancel();
        }
    }
}
