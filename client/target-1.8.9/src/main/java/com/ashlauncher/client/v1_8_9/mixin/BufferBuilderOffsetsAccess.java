package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The offset a buffer builder adds to every position it is given, which the
 * game keeps private. Faster clouds writes the clouds' vertices itself, and
 * adds the shared builder's offset as the builder would.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderOffsetsAccess {

    @Accessor("offsetX")
    double ash$offsetX();

    @Accessor("offsetY")
    double ash$offsetY();

    @Accessor("offsetZ")
    double ash$offsetZ();
}
