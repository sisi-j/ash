package com.ashlauncher.client.v1_21_11.mixin;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.v1_21_11.GuiGraphicsHudSurface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ash's crosshair on 1.21.11: drawn in place of the game's, from inside the
 * game's own crosshair drawing.
 *
 * <p>Inside {@code Gui.renderCrosshair}, at the one {@code blitSprite} that
 * draws the {@code hud/crosshair} sprite - not a replacement of Fabric API's
 * crosshair element. That method draws the attack-cooldown indicator straight
 * after the crosshair, and decides third person, spectators and the debug
 * screen's 3D crosshair before it; replacing the element would lose the
 * indicator and every one of those rules. Wrapping the one call keeps them
 * all, and if this does not land, the game's own crosshair draws: never none,
 * never two. Every name here was read from the mapped jar: see
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 1.1-1.3.
 *
 * <p>Matched by sprite, not by ordinal: the indicator's "full" and
 * "background" sprites go through the same overload in the same method.
 */
@Mixin(Gui.class)
abstract class GuiCrosshairMixin {

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIII)V"))
    private void ash$drawCrosshair(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite, int x, int y,
            int width, int height, Operation<Void> original) {
        boolean theCrosshair = "minecraft".equals(sprite.getNamespace()) && "hud/crosshair".equals(sprite.getPath());
        if (!theCrosshair || !CrosshairHook.draw(new GuiGraphicsHudSurface(graphics))) {
            original.call(graphics, pipeline, sprite, x, y, width, height);
        }
    }
}
