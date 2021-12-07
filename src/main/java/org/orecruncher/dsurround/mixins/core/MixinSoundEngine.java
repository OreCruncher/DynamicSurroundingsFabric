package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.sound.SoundEngine;
import org.orecruncher.dsurround.Client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

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
}