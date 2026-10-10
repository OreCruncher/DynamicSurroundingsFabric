package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sounds.ChannelAccess;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.SoundFXProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChannelAccess.ChannelHandle.class)
public abstract class MixinChannelHandleAccessor {

    @Inject(method = "release()V", at = @At("HEAD"))
    private void dsurround$release(CallbackInfo ci) {
        try {
            var channel = ((ChannelAccess.ChannelHandle) (Object) this).channel;
            if (channel != null) {
                SoundFXProcessor.stopSoundPlay(channel);
            }
        } catch (Throwable t) {
            MixinHelpers.LOGGER.error(t, "Unable to stop sound play");
        }
    }
}
