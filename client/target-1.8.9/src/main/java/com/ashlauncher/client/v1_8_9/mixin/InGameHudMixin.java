package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.v1_8_9.LegacyHudSurface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ash's crosshair on 1.8.9: drawn in place of the game's, at the one call in
 * {@code InGameHud.render} that draws it.
 *
 * <p>A mixin because Legacy Fabric API's HUD callback can only draw on top of
 * the vanilla HUD, never take anything away. The call is the only
 * {@code drawTexture} in {@code render}, and it is reached only when
 * {@code showCrosshair()} said so - so the game's own rules still decide when
 * there is a crosshair at all, third person included, where 1.8.9 shows one.
 * If this does not land, the game's own crosshair draws. Every name here was
 * read from the mapped jar: see
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 1.4.
 */
@Mixin(InGameHud.class)
abstract class InGameHudMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(IIIIII)V"))
    private void ash$drawCrosshair(InGameHud hud, int x, int y, int u, int v, int width, int height,
            Operation<Void> original) {
        if (CrosshairHook.draw(new LegacyHudSurface(MinecraftClient.getInstance()))) {
            // The game turned blending on before its crosshair and turns it off
            // itself further down; ash's fill leaves it off, so it goes back
            // on for everything the game draws in between.
            GlStateManager.enableBlend();
            return;
        }
        original.call(hud, x, y, u, v, width, height);
    }
}
