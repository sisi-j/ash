package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.cosmetics.CapeTextures;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capes on 1.8.9 (research 0012). Legacy Yarn 604's names are swapped here:
 * {@code getSkinId()} is the cape texture - it returns the player list
 * entry's cape, and is what {@code CapeFeatureRenderer} draws with - while
 * {@code getCapeId()} is the skin. Checked in the bytecode, not taken from
 * the names.
 */
@Mixin(AbstractClientPlayerEntity.class)
abstract class AbstractClientPlayerEntityCapeMixin {

    @Inject(method = "getSkinId", at = @At("RETURN"), cancellable = true)
    private void ash$cape(CallbackInfoReturnable<Identifier> info) {
        Identifier cape = info.getReturnValue();
        Identifier ash = CapeTextures.capeFor(((AbstractClientPlayerEntity) (Object) this).getUuid(), cape);
        if (ash != cape) {
            info.setReturnValue(ash);
        }
    }
}
