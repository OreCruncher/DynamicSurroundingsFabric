package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.SoundEngine;
import org.lwjgl.openal.EXTEfx;
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
            // From the original code - not sure what it is setting
            intBuffer.put(6554).put(1);
            // Increase send channels
            intBuffer.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(4).put(0);
            return intBuffer.flip();
        }
        // Leave the buffer intact
        return intBuffer;
    }
}
