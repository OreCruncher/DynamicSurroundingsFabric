package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Biome.class)
public interface BiomeAccessor {

/*
    @Accessor
    Biome.Category getCategory();
*/
}
