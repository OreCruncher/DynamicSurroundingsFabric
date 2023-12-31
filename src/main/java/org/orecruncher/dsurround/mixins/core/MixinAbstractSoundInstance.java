package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface MixinAbstractSoundInstance {

    @Accessor("volume")
    float dsurround_getRawVolume();

    @Accessor("pitch")
    float dsurround_getRawPitch();
}
