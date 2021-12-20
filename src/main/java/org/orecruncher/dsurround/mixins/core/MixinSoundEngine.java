package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.Source;
import org.apache.logging.log4j.Logger;
import org.orecruncher.dsurround.Client;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    @Shadow @Final
    static Logger LOGGER;

    /**
     * Modify the number of streaming sounds that can be handled by the underlying sound engine.  The number of
     * channels to set is driven by config settings.
     *
     * @param v Existing value for the number of streaming sounds (should be 8)
     * @return The quantity of streaming sounds (should be at least 8)
     */
    @ModifyConstant(method = "init(Ljava/lang/String;)V", constant = @Constant(intValue = 8))
    public int dsurround_initialize(int v) {
        return Client.Config.soundSystem.streamingChannels;
    }

// TODO: Delete if really fixed
//    @Inject(method = "release",
//            at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
//                    target = "Ljava/lang/IllegalStateException;<init>(Ljava/lang/String;)V"),
//            cancellable = true)
//    public void rlsH(Source source, CallbackInfo ci) {
//        LOGGER.info("Source is being released incorrectly: " + source);
//        ci.cancel();
//    }

}
