package org.orecruncher.dsurround.mixins.core;

import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Biome.class)
public abstract class MixinBiome {

    /**
     * Get fog color from Dynamic Surroundings' config if available.
     *
     * @param cir Mixin callback result
     */
    /*
    @Inject(method = "getFogColor()I", at = @At("HEAD"), cancellable = true)
    public void dsurround$getFogColor(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.fogOptions.enableFogEffects && MixinHelpers.fogOptions.enableBiomeFog) {
            ReflectionHelper.cast(this, Biome.class)
                    .map(MixinHelpers.BIOME_LIBRARY::getBiomeInfoWeak)
                    .map(BiomeInfo::getFogColor)
                    .ifPresent(color -> cir.setReturnValue(color.getValue()));
        }
    }

     */

    /**
     * Check the biome configuration for a background soundtrack for the biome. If one is present,
     * return it. Otherwise, let Minecraft do its thing.
     *
     * NOTE: If a biome has been configured with a background sound via data pack, it is folded into
     * the selection weight table.
     */
    /*
    @Inject(method = "getBackgroundMusic()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void dsurround$getBackgroundMusic(CallbackInfoReturnable<Optional<Music>> cir) {
        ReflectionHelper.cast(this, Biome.class)
                .map(MixinHelpers.BIOME_LIBRARY::getBiomeInfoWeak)
                .map(info -> info.getBackgroundMusic(Randomizer.current()))
                .ifPresent(cir::setReturnValue);
    }

     */
}
