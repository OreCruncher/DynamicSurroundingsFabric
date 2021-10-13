package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface MixinSoundEngineAccessor {

    @Accessor("devicePointer")
    long getDevicePointer();

    @Accessor("devicePointer")
    void setDevicePointer(long devicePointer);

    @Accessor("contextPointer")
    long getContextPointer();

    @Accessor("contextPointer")
    void setContextPointer(long contextPointer);
}
