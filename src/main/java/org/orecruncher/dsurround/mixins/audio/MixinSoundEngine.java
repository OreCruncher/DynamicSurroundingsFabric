package org.orecruncher.dsurround.mixins.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Library;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;
import org.orecruncher.dsurround.mixinutils.ISoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

import java.nio.IntBuffer;

@Mixin(Library.class)
public class MixinSoundEngine implements ISoundEngine {

    @Shadow
    private long currentDevice;

    public long dsurround_getDevicePointer() {
        return this.currentDevice;
    }

    /**
     * This will resize the capability buffer to accommodate additional settings
     */
    @ModifyConstant(method = "init(Ljava/lang/String;Z)V", constant = @Constant(intValue = 3))
    private int dsurround_modifyIntBufferSize(int size) {
        return AudioUtilities.doEnhancedSounds() ? 5 : 3;
    }

    /**
     * Rewrite the capability buffer.  We only do this if advanced processing is enabled.
     */
    @WrapOperation(method = "init(Ljava/lang/String;Z)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J", remap = false))
    private long dsurround_buildCapabilities(long deviceHandle, IntBuffer attrList, Operation<Long> original) {
        if (AudioUtilities.doEnhancedSounds()) {
            // Buffer should have been resized by the constant modification above
            attrList.clear();
            // From the original code
            attrList.put(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT).put(ALC10.ALC_TRUE);
            // Increase sends channels
            attrList.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(4);
            // Done!
            attrList.put(0);
            attrList.flip();
            return ALC10.alcCreateContext(deviceHandle, attrList);
        } else {
            return original.call(deviceHandle, attrList);
        }
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
        var config = ContainerManager.resolve(Configuration.SoundSystem.class);
        return config.streamingChannels;
    }
}
