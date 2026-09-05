package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sounds.ChannelAccess;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
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
            ReflectionHelper.cast(this, ChannelAccess.ChannelHandle.class)
                    .ifPresent( c -> {
                        if (c.channel != null) {
                            SoundFXProcessor.stopSoundPlay(c.channel);
                        }
                    });
        } catch (Throwable t) {
            MixinHelpers.LOGGER.error(t, "Unable to stop sound play");
        }
    }
}
