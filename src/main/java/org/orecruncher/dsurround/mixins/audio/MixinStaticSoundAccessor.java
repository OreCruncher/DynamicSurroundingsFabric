package org.orecruncher.dsurround.mixins.audio;

import net.minecraft.client.sound.StaticSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

@Mixin(StaticSound.class)
public interface MixinStaticSoundAccessor {

    @Accessor("sample")
    ByteBuffer getSample();

    @Accessor("format")
    AudioFormat getFormat();

    @Accessor("format")
    @Mutable
    void setFormat(AudioFormat format);

}
