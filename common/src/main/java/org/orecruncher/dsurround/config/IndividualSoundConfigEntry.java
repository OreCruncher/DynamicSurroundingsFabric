package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.registry.Comparers;

public class IndividualSoundConfigEntry implements Comparable<IndividualSoundConfigEntry> {

    public static final Codec<IndividualSoundConfigEntry> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("soundEventId").forGetter(info -> info.soundEventId),
                    Codec.intRange(0, 400).optionalFieldOf("volumeScale", 100).forGetter(info -> info.volumeScale),
                    Codec.BOOL.optionalFieldOf("block", false).forGetter(info -> info.block),
                    Codec.BOOL.optionalFieldOf("cull", false).forGetter(info -> info.cull)
            ).apply(instance, IndividualSoundConfigEntry::new));

    public ResourceLocation soundEventId;
    public String soundEventIdProjected;
    public int volumeScale;
    public boolean block;
    public boolean cull;

    public IndividualSoundConfigEntry(ResourceLocation id, Integer volumeScale, Boolean block, Boolean cull) {
        this.soundEventId = id;
        this.soundEventIdProjected = id.toString();
        this.volumeScale = Mth.clamp(volumeScale, 0, 400);
        this.block = block;
        this.cull = cull;
    }

    IndividualSoundConfigEntry(IndividualSoundConfigEntry source) {
        this.soundEventId = source.soundEventId;
        this.soundEventIdProjected = source.soundEventIdProjected;
        this.volumeScale = source.volumeScale;
        this.block = source.block;
        this.cull = source.cull;
    }

    public IndividualSoundConfigEntry(ResourceLocation id) {
        this(id, 100, false, false);
    }

    public static IndividualSoundConfigEntry createDefault(final SoundEvent event) {
        return new IndividualSoundConfigEntry(event.getLocation());
    }

    public static IndividualSoundConfigEntry from(IndividualSoundConfigEntry source) {
        return new IndividualSoundConfigEntry(source);
    }

    public boolean isNotDefault() {
        return this.volumeScale != 100 || this.block || this.cull;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append(this.soundEventId.toString()).append("{");
        if (this.cull)
            builder.append("cull ");
        if (this.block)
            builder.append("block ");
        builder.append("}");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return this.soundEventId.hashCode();
    }

    @Override
    public int compareTo(@NotNull IndividualSoundConfigEntry o) {
        return Comparers.IDENTIFIER_NATURAL_COMPARABLE.compare(this.soundEventId, o.soundEventId);
    }
}
