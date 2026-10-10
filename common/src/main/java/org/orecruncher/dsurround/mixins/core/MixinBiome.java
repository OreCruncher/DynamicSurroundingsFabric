package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            // Called for every sample of the fog color blend, hundreds of times a frame: no allocation here
            var info = MixinHelpers.biomeLibrary().findBiomeInfo((Biome) (Object) this);
            if (info != null) {
                var color = info.getFogColor();
                if (color != null)
                    cir.setReturnValue(color.getValue());
            }
        }
    }

     */
}
