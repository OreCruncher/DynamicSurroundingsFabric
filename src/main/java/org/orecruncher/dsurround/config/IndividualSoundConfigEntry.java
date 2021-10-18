package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class IndividualSoundConfigEntry {

    public static final Codec<IndividualSoundConfigEntry> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Identifier.CODEC.fieldOf("soundEventId").forGetter(info -> info.soundEventId),
                    Codec.intRange(0, 400).optionalFieldOf("volumeScale", 100).forGetter(info -> info.volumeScale),
                    Codec.BOOL.optionalFieldOf("block", false).forGetter(info -> info.block),
                    Codec.BOOL.optionalFieldOf("cull", false).forGetter(info -> info.cull),
                    Codec.BOOL.optionalFieldOf("startup", false).forGetter(info -> info.startup)
            ).apply(instance, IndividualSoundConfigEntry::new));

    public Identifier soundEventId;
    public int volumeScale;
    public boolean block;
    public boolean cull;
    public boolean startup;

    IndividualSoundConfigEntry(Identifier id, int volumeScale, boolean block, boolean cull, boolean startup) {
        this.soundEventId = id;
        this.volumeScale = MathHelper.clamp(volumeScale, 0, 400);
        this.block = block;
        this.cull = cull;
        this.startup = startup;
    }

    IndividualSoundConfigEntry(IndividualSoundConfigEntry source) {
        this.soundEventId = source.soundEventId;
        this.volumeScale = source.volumeScale;
        this.block = source.block;
        this.cull = source.cull;
        this.startup = source.startup;
    }

    public IndividualSoundConfigEntry(String id) {
        this(new Identifier(id), 100, false, false, false);
    }

    public static IndividualSoundConfigEntry createDefault(final SoundEvent event) {
        return new IndividualSoundConfigEntry(event.getId().toString());
    }

    public static IndividualSoundConfigEntry from(IndividualSoundConfigEntry source) {
        return new IndividualSoundConfigEntry(source);
    }

    public boolean isNotDefault() {
        return this.volumeScale != 100 || this.block || this.cull || this.startup;
    }
}
