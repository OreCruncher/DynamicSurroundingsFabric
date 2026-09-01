package org.orecruncher.dsurround.mixins.core;

import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
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
    @Inject(method = "getFogColor()I", at = @At("HEAD"), cancellable = true)
    public void dsurround_getFogColor(CallbackInfoReturnable<Integer> cir) {
        if (MixinHelpers.fogOptions.enableFogEffects && MixinHelpers.fogOptions.enableBiomeFog) {
            var biome = ReflectionHelper.cast(this, Biome.class);
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
        var biome = ReflectionHelper.cast(this, Biome.class);
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
