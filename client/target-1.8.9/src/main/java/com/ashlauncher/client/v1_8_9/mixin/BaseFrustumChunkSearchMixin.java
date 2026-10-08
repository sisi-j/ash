package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.perf.FrustumPlanes;
import com.ashlauncher.client.v1_8_9.FasterChunkSearch;
import net.minecraft.client.render.BaseFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Faster chunk search on 1.8.9, in the frustum: one corner per plane instead
 * of eight, with the game's answer bit for bit ({@link FrustumPlanes}). The
 * chunk search is what asks most, but everything the game culls by the
 * frustum asks here, and gets the same answer either way.
 */
@Mixin(BaseFrustum.class)
abstract class BaseFrustumChunkSearchMixin {

    @Shadow
    public float[][] homogeneousCoordinates;

    /**
     * @author ash
     * @reason faster chunk search (#104): the deciding corner alone when on;
     *     the game's own eight corners, as {@link FrustumPlanes#gameTest}
     *     copies them, when off or when a plane is not a finite number
     */
    @Overwrite
    public boolean isInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (FasterChunkSearch.on()) {
            int answer = FrustumPlanes.test(homogeneousCoordinates, minX, minY, minZ, maxX, maxY, maxZ);
            if (answer != FrustumPlanes.ASK_THE_GAME) {
                return answer == FrustumPlanes.INSIDE;
            }
        }
        return FrustumPlanes.gameTest(homogeneousCoordinates, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
