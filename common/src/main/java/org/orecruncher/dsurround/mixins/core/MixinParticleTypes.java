package org.orecruncher.dsurround.mixins.core;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import org.apache.commons.lang3.NotImplementedException;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Avert your eyes! Nothing to see here. Since Dynamic Surroundings is a 100% client side mod with no server
 * specific logic particle registration is a bit weird. To work around it the mod hooks into the Minecraft initialization
 * path for particles and puts the data into the Minecraft registrations.
 */
@Mixin(ParticleTypes.class)
public class MixinParticleTypes {

    @Inject(method = "<clinit>", at = @At("HEAD"))
    private static void dsurround$staticHook(CallbackInfo ci) {
        DSurroundParticleTypes.WATER_RIPPLE = dsurround$register(Constants.asId("water_ripple").toString(), false);
        DSurroundParticleTypes.WATER_RIPPLE_PIXELATED = dsurround$register(Constants.asId("water_ripple_pixelated").toString(), false);
        DSurroundParticleTypes.WATERFALL_CASCADE = dsurround$register(Constants.asId("waterfall_cascade").toString(), false);
    }

    @Invoker("register")
    private static SimpleParticleType dsurround$register(String string, boolean bl) {
        throw new NotImplementedException();
    }
}
