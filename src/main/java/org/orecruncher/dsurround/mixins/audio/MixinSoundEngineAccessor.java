package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SoundEngine.class)
public interface MixinSoundEngineAccessor {

    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> dsurround_getSources();

    @Accessor("listener")
    Listener dsurround_getListener();
}
