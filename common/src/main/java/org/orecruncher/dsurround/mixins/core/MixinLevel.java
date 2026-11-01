package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public class MixinLevel {

    /**
     * The storm's strength in place of the world's rain level while vanilla's rain is ticked (see
     * MixinHelpers.rainTickThread): how many splashes, and the rain sound. Only then, and only on the thread ticking it:
     * the sky, isRaining() and everything else reading the level, the server among them, are left alone.
     */
    @ModifyReturnValue(method = "getRainLevel(F)F", at = @At("RETURN"), require = 0)
    private float dsurround$stormRainLevelTicked(float vanillaLevel) {
        return MixinHelpers.inRainTick() ? MixinHelpers.stormRainLevel(vanillaLevel) : vanillaLevel;
    }
}
