package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Biome.class)
public abstract class MixinBiome implements IBiomeExtended {

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

    @Invoker("getTemperature")
    public abstract float dsurround_getTemperature(BlockPos pos);

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

    /**
     * Hook obtaining background music for the biome. If there is a biome sound already configured
     * via a data pack, use that. Otherwise, make a selection based on our configuration.
     */
    @WrapOperation(method = "getBackgroundMusic()Ljava/util/Optional;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeSpecialEffects;getBackgroundMusic()Ljava/util/Optional;"))
    private Optional<Music> dsurround_getBackgroundMusic(BiomeSpecialEffects instance, Operation<Optional<Music>> original) {
        var result = original.call(instance);
        if (result.isPresent())
            return result;
        return this.dsurround_info.getBackgroundMusic(Randomizer.current());
    }
}
