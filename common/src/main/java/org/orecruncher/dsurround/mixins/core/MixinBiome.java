package org.orecruncher.dsurround.mixins.core;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Biome.class)
public abstract class MixinBiome implements IBiomeExtended {

    @Final
    @Shadow
    private Biome.ClimateSettings climateSettings;

    @Final
    @Shadow
    private BiomeSpecialEffects specialEffects;

    @Override
    public BiomeSpecialEffects dsurround_getSpecialEffects() {
        return this.specialEffects;
    };

    @Override
    public Biome.ClimateSettings dsurround_getWeather() {
        return this.climateSettings;
    }

    @Invoker("getTemperature")
    public abstract float dsurround_getTemperature(BlockPos pos);

    /**
     * Get fog color from Dynamic Surroundings' config if available.
     *
     * @param cir Mixin callback result
     */
    @Inject(method = "getFogColor()I", at = @At("HEAD"), cancellable = true)
    public void dsurround_getFogColor(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.fogOptions.enableFogEffects && MixinHelpers.fogOptions.enableBiomeFog) {
            Optional<Biome> biome = ReflectionHelper.cast(this);
            biome.ifPresent(b -> {
                var info = MixinHelpers.BIOME_LIBRARY.getBiomeInfo(b);
                if (info != null) {
                    var color = info.getFogColor();
                    if (color != null)
                        cir.setReturnValue(color.getValue());
                }
            });
        }
    }

    /**
     * Check the biome configuration for a background soundtrack for the biome. If one is present,
     * return it. Otherwise, let Minecraft do its thing.
     *
     * NOTE: If a biome has been configured with a background sound via data pack, it is folded into
     * the selection weight table.
     */
    @Inject(method = "getBackgroundMusic()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void dsurround_getBackgroundMusic(CallbackInfoReturnable<Optional<Music>> cir) {
        Optional<Biome> biome = ReflectionHelper.cast(this);
        biome.ifPresent(b -> {
            var info = MixinHelpers.BIOME_LIBRARY.getBiomeInfo(b);
            if (info == null) {
                // Can be null after things like a teleport
                cir.setReturnValue(Optional.empty());
            } else {
                var result = info.getBackgroundMusic(Randomizer.current());
                if (result.isPresent())
                    cir.setReturnValue(result);
            }
        });
    }
}
