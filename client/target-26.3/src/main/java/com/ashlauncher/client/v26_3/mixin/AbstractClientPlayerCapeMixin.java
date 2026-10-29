package com.ashlauncher.client.v26_3.mixin;

import com.ashlauncher.client.v26_3.cosmetics.CapeTextures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capes on 26.3: the skin the game draws a player with, with ash's cape in
 * it when the player wears one (research 0012). Every renderer that draws a
 * cape - the cape layer, an elytra without its own texture - reads it here.
 */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerCapeMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void ash$cape(CallbackInfoReturnable<PlayerSkin> info) {
        PlayerSkin skin = info.getReturnValue();
        PlayerSkin withCape = CapeTextures.withCape(((AbstractClientPlayer) (Object) this).getUUID(), skin);
        if (withCape != skin) {
            info.setReturnValue(withCape);
        }
    }
}
