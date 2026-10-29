package com.ashlauncher.client.v26_3.mixin;

import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.v26_3.GuiGraphicsHudSurface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ash's crosshair on 26.3: drawn in place of the game's, from inside the
 * game's own crosshair drawing.
 *
 * <p>Inside {@code Hud.extractCrosshair}, at the one {@code blitSprite} that
 * draws the {@code hud/crosshair} sprite - not a replacement of Fabric API's
 * crosshair element. That method draws the attack-cooldown indicator straight
 * after the crosshair, and decides third person, spectators and the debug
 * screen's 3D crosshair before it; replacing the element would lose the
 * indicator and every one of those rules. Wrapping the one call keeps them
 * all, and if this does not land, the game's own crosshair draws: never none,
 * never two. 26.3 moved the HUD out of {@code Gui}, which still exists as
 * another class, into {@code Hud}, and renamed drawing to extracting; the
 * sprite, its centre and the call are as they were on 1.21.11
 * ({@code docs/research/0016}, section 2).
 *
 * <p>Matched by sprite, not by ordinal: the indicator's "full" and
 * "background" sprites go through the same overload in the same method.
 */
@Mixin(Hud.class)
abstract class HudCrosshairMixin {

    @WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIII)V"))
    private void ash$drawCrosshair(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y,
            int width, int height, Operation<Void> original) {
        boolean theCrosshair = "minecraft".equals(sprite.getNamespace()) && "hud/crosshair".equals(sprite.getPath());
        // The sprite is 15 square, drawn at ((width - 15) / 2, (height - 15)
        // / 2), with its plus centred on pixel 7 - read from the sprite
        // itself. So the game's own centre is x + 7, y + 7, which on an
        // even-sized screen is one pixel up and left of the middle.
        if (!theCrosshair || !CrosshairHook.draw(new GuiGraphicsHudSurface(graphics), x + 7, y + 7)) {
            original.call(graphics, pipeline, sprite, x, y, width, height);
        }
    }
}
