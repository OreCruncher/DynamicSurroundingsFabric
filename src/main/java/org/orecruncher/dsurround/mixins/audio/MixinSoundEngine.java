package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.SoundEngine;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.nio.IntBuffer;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    /**
     * This will resize the capability buffer to accommodate additional settings
     */
    @ModifyConstant(method = "init(Ljava/lang/String;Z)V", constant = @Constant(intValue = 3))
    private int modifyIntBufferSize(int size) {
        return AudioUtilities.doEnhancedSounds() ? 5 : 3;
    }

    /**
     * Rewrite the capability buffer - wee!
     * NOTE: The dev plugin for Intellij does not like the method signature - just ignore.
     */
    @ModifyVariable(method = "init(Ljava/lang/String;Z)V", at = @At(value = "STORE"), name = "intBuffer")
    private IntBuffer buildCapabilities(IntBuffer intBuffer) {
        if (AudioUtilities.doEnhancedSounds()) {
            // Buffer should have been resized by the constant modification above
            intBuffer.clear();
            // From the original code
            intBuffer.put(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT).put(ALC10.ALC_TRUE);
            // Increase send channels
            intBuffer.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(4).put(0);
            return intBuffer.flip();
        }
        // Leave the buffer intact
        return intBuffer;
    }

    /**
     * Modify the number of streaming sounds that can be handled by the underlying sound engine.  The number of
     * channels to set is driven by config settings.
     *
     * @param v Existing value for the number of streaming sounds (should be 8)
     * @return The quantity of streaming sounds (should be at least 8)
     */
    @ModifyConstant(method = "init(Ljava/lang/String;Z)V", constant = @Constant(intValue = 8))
    public int dsurround_initialize(int v) {
        return Client.Config.soundSystem.streamingChannels;
    }
}
