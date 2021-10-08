package org.orecruncher.dsurround.mixins;

import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.xface.IBiomeExtended;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Biome.class)
public class MixinBiome implements IBiomeExtended {

    private BiomeInfo dsurround_info;

    @Override
    public BiomeInfo getInfo() {
        return this.dsurround_info;
    }

    @Override
    public void setInfo(BiomeInfo info) {
        this.dsurround_info = info;
    }
}
