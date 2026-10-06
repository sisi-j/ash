package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.freelook.FreelookHook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Freelook's angles in the camera entity's own rotation fields, for the length
 * of the world's drawing only. See {@code GameRendererFreelookMixin}.
 */
public final class FreelookView {

    /** The entity whose fields hold freelook's angles right now, or null. */
    private static Entity turned;
    private static float yaw;
    private static float pitch;
    private static float prevYaw;
    private static float prevPitch;

    private FreelookView() {
    }

    /** Puts freelook's angles in, if it is held. Interpolation is off: the angles are where the mouse put them. */
    public static void turnTo() {
        turnBack();
        Entity entity = MinecraftClient.getInstance().getCameraEntity();
        if (entity == null || !FreelookHook.active()) {
            return;
        }
        turned = entity;
        yaw = entity.yaw;
        pitch = entity.pitch;
        prevYaw = entity.prevYaw;
        prevPitch = entity.prevPitch;
        entity.yaw = entity.prevYaw = FreelookHook.yaw(entity.yaw);
        entity.pitch = entity.prevPitch = FreelookHook.pitch(entity.pitch);
    }

    /** Puts the entity's own angles back, exactly as they were. */
    public static void turnBack() {
        if (turned == null) {
            return;
        }
        turned.yaw = yaw;
        turned.pitch = pitch;
        turned.prevYaw = prevYaw;
        turned.prevPitch = prevPitch;
        turned = null;
    }

    /** Whether freelook's angles are in the fields right now, for the smoke test. */
    static boolean turned() {
        return turned != null;
    }
}
