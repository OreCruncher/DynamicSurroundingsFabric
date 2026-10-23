package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.registry.Comparers;

import java.util.Optional;

public class IndividualSoundConfigEntry implements Comparable<IndividualSoundConfigEntry> {

    // "block" and "cull" are how older files said what "action" says now. They are read when an entry has no
    // action, and never written.
    public static final Codec<IndividualSoundConfigEntry> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("soundEventId").forGetter(info -> info.soundEventId),
                    Codec.intRange(0, 400).optionalFieldOf("volumeScale", 100).forGetter(info -> info.volumeScale),
                    SoundAction.CODEC.optionalFieldOf("action").forGetter(info -> info.action == SoundAction.DEFAULT ? Optional.empty() : Optional.of(info.action)),
                    Codec.BOOL.optionalFieldOf("block", false).forGetter(info -> false),
                    Codec.BOOL.optionalFieldOf("cull", false).forGetter(info -> false)
            ).apply(instance, IndividualSoundConfigEntry::decoded));

    public ResourceLocation soundEventId;
    public String soundEventIdProjected;
    public int volumeScale;
    public SoundAction action;

    public IndividualSoundConfigEntry(ResourceLocation id, int volumeScale, SoundAction action) {
        this.soundEventId = id;
        this.soundEventIdProjected = id.toString();
        this.volumeScale = Mth.clamp(volumeScale, 0, 400);
        this.action = action;
    }

    IndividualSoundConfigEntry(IndividualSoundConfigEntry source) {
        this.soundEventId = source.soundEventId;
        this.soundEventIdProjected = source.soundEventIdProjected;
        this.volumeScale = source.volumeScale;
        this.action = source.action;
    }

    public IndividualSoundConfigEntry(ResourceLocation id) {
        this(id, 100, SoundAction.DEFAULT);
    }

    /**
     * An entry as read from a file. Without an action, an older file's flags say what it is; a sound set to both
     * cull and block is culled, as the configuration screen always showed it.
     */
    private static IndividualSoundConfigEntry decoded(ResourceLocation id, int volumeScale, Optional<SoundAction> action, boolean block, boolean cull) {
        var resolved = action.orElse(cull ? SoundAction.CULL : block ? SoundAction.BLOCK : SoundAction.DEFAULT);
        return new IndividualSoundConfigEntry(id, volumeScale, resolved);
    }

    public static IndividualSoundConfigEntry createDefault(final SoundEvent event) {
        return new IndividualSoundConfigEntry(event.getLocation());
    }

    public static IndividualSoundConfigEntry from(IndividualSoundConfigEntry source) {
        return new IndividualSoundConfigEntry(source);
    }

    public boolean isNotDefault() {
        return this.volumeScale != 100 || this.action != SoundAction.DEFAULT;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append(this.soundEventId.toString()).append("{");
        if (this.action != SoundAction.DEFAULT)
            builder.append(this.action.getSerializedName());
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
