package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface MixinSoundManagerAccessor {

    @Accessor("soundEngine")
    SoundEngine dsurround_getSoundSystem();
}
