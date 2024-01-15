package org.orecruncher.dsurround.sound;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.lib.IdentityUtils;

public class SoundCodecHelpers {

    public static final Codec<SoundSource> SOUND_CATEGORY_CODEC = Codec.STRING.xmap(SoundCodecHelpers::lookupSoundSource, SoundSource::getName);
    public static final Codec<SoundInstance.Attenuation> ATTENUATION_CODEC = Codec.STRING.xmap(SoundCodecHelpers::lookupAttenuation, SoundInstance.Attenuation::name);
    public static final Codec<SoundEvent> SOUND_EVENT_CODEC = Codec.either(IdentityUtils.CODEC, SoundEvent.DIRECT_CODEC)
            .xmap(either -> either.map(SoundEvent::createVariableRangeEvent, x -> x), Either::right);

    private static SoundSource lookupSoundSource(String string) {
        for (var c : SoundSource.values())
            if (c.getName().equalsIgnoreCase(string))
                return c;
        return SoundSource.AMBIENT;
    }

    private static SoundInstance.Attenuation lookupAttenuation(String string) {
        for (var c : SoundInstance.Attenuation.values())
            if (c.name().equalsIgnoreCase(string))
                return c;
        return SoundInstance.Attenuation.LINEAR;
    }

}
