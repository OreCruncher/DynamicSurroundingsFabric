package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class MixinBiome implements IBiomeExtended {

    @Unique
    private BiomeInfo dsurround_info;

    @Final
    @Shadow
    private Biome.ClimateSettings climateSettings;

    @Override
    public BiomeInfo dsurround_getInfo() {
        return this.dsurround_info;
    }

    @Override
    public void dsurround_setInfo(BiomeInfo info) {
        this.dsurround_info = info;
    }

    @Override
    public Biome.ClimateSettings dsurround_getWeather() {
        return this.climateSettings;
    }

    /**
     * Obtain fog color from Dynamic Surroundings' config if available.
     *
     * @param cir Mixin callback result
     */
    @Inject(method = "getFogColor()I", at = @At("HEAD"), cancellable = true)
    public void dsurround_getFogColor(CallbackInfoReturnable<Integer> cir) {
        if (this.dsurround_info != null) {
            var color = this.dsurround_info.getFogColor();
            if (color != null)
                cir.setReturnValue(color.getValue());
        }
    }
}
