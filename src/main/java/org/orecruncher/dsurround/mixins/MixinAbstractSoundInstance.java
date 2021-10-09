package org.orecruncher.dsurround.mixins;

import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface MixinAbstractSoundInstance {

    @Accessor("volume")
    float getRawVolume();

    @Accessor("pitch")
    float getRawPitch();
}
