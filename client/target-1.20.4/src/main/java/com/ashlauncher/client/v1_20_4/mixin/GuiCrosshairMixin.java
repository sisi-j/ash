package com.ashlauncher.client.v1_20_4.mixin;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.v1_20_4.GuiGraphicsHudSurface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ash's crosshair on 1.20.4: drawn in place of the game's, from inside the
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
 * {@code docs/research/0015-ash-s-hooks-in-1-20-4.md}, section 2: 1.20.4's
 * {@code blitSprite} has no render pipeline argument.
 *
 * <p>Matched by sprite, not by ordinal: the indicator's "full" and
 * "background" sprites go through the same overload in the same method.
 */
@Mixin(Gui.class)
abstract class GuiCrosshairMixin {

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void ash$drawCrosshair(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height,
            Operation<Void> original) {
        boolean theCrosshair = "minecraft".equals(sprite.getNamespace()) && "hud/crosshair".equals(sprite.getPath());
        // The sprite is 15 square, drawn at ((width - 15) / 2, (height - 15)
        // / 2), with its plus centred on pixel 7 - read from the sprite
        // itself. So the game's own centre is x + 7, y + 7, which on an
        // even-sized screen is one pixel up and left of the middle.
        if (!theCrosshair || !CrosshairHook.draw(new GuiGraphicsHudSurface(graphics), x + 7, y + 7)) {
            original.call(graphics, sprite, x, y, width, height);
        }
    }
}
