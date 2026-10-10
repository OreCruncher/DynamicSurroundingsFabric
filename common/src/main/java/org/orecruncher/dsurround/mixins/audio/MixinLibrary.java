package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Library;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.nio.IntBuffer;

@Mixin(Library.class)
public class MixinLibrary {

    /**
     * Increase the number of auxiliary sends for a sound channel. This is so that enhanced processing can do
     * its thing.
     */
    @Inject(method = "createAttributes(Lorg/lwjgl/system/MemoryStack;Z)Ljava/nio/IntBuffer;", at = @At(value= "INVOKE", target="Lorg/lwjgl/openal/ALC10;alcGetInteger(JI)I", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void dsurround$setChannels(MemoryStack stack, boolean enableHrtf, CallbackInfoReturnable<IntBuffer> cir, int maxAttributes, IntBuffer attr) {
        attr.put(EXTEfx.ALC_MAX_AUXILIARY_SENDS).put(4);
    }

    /**
     * Modify the number of streaming sounds that can be handled by the underlying sound engine.  The number of
     * channels to set is driven by config settings.
     *
     * @param v Existing value for the number of streaming sounds (should be 8)
     * @return The quantity of streaming sounds (should be at least 8)
     */
    @ModifyConstant(method = "init(Ljava/lang/String;Lcom/mojang/blaze3d/audio/DeviceList;Z)V", constant = @Constant(intValue = 8))
    public int dsurround$initialize(int v) {
        var config = ContainerManager.resolve(Configuration.SoundSystem.class);
        return config.streamingChannels;
    }
}
