package org.orecruncher.dsurround.mixins.audio;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

@Mixin(SoundBuffer.class)
public interface MixinStaticSoundAccessor {

    @Accessor("data")
    ByteBuffer dsurround_getSample();

    @Accessor("format")
    AudioFormat dsurround_getFormat();

    @Accessor("format")
    @Mutable
    void dsurround_setFormat(AudioFormat format);

}
