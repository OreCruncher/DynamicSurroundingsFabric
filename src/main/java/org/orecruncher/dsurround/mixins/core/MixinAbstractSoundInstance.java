package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface MixinAbstractSoundInstance {

    @Accessor("volume")
    float getRawVolume();

    @Accessor("volume")
    void setRawVolume(float vol);

    @Accessor("pitch")
    float getRawPitch();

    @Accessor("pitch")
    void setRawPitch(float pitch);
}
