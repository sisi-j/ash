package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The host a join from launch arguments ({@code --server}) went to, which
 * 1.8.9 keeps only in a private field: it sets no server entry for that join.
 * ash's own Join reaches 1.8.9 this way. See {@code docs/research/0009}, 1.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccess {

    @Accessor("serverAddress")
    String ash$serverAddress();
}
