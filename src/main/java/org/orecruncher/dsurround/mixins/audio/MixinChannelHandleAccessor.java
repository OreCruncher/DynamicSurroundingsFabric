package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import org.orecruncher.dsurround.mixinutils.IChannelHandle;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChannelAccess.ChannelHandle.class)
public abstract class MixinChannelHandleAccessor implements IChannelHandle {

    @Accessor("channel")
    public abstract Channel dsurround_getSource();

    @Inject(method = "release()V", at = @At("HEAD"))
    private void dsurround_release(CallbackInfo ci) {
        try {
            SoundFXProcessor.stopSoundPlay(this.dsurround_getSource());
        } catch (Throwable t) {
            MixinHelpers.LOGGER.error(t, "Unable to stop sound play");
        }
    }
}
