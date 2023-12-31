package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface MixinSoundManagerAccessor {

    @Accessor("soundSystem")
    SoundSystem dsurround_getSoundSystem();
}
