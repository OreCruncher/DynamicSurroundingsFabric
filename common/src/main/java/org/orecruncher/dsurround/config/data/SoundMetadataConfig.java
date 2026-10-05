package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.sound.SoundCodecHelpers;

import java.util.List;
import java.util.Optional;

public record SoundMetadataConfig(Optional<String> title, Optional<String> subtitle, Optional<SoundSource> category, List<CreditEntry> credits) {

    public static final Codec<SoundMetadataConfig> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("ds_title").forGetter(SoundMetadataConfig::title),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(SoundMetadataConfig::subtitle),
            // Case-insensitive; an unknown name is logged and treated as ambient, keeping the rest of the entry
            SoundCodecHelpers.SOUND_CATEGORY_CODEC.optionalFieldOf("ds_category").forGetter(SoundMetadataConfig::category),
            Codec.list(CreditEntry.CODEC).optionalFieldOf("ds_credits", ImmutableList.of()).forGetter(SoundMetadataConfig::credits))
            .apply(instance, SoundMetadataConfig::new));

    /**
     * Indicates whether the settings in the instance are the default settings.
     *
     * @return true if the properties are the same as defaults; false otherwise
     */
    public boolean isDefault() {
        return this.title.isEmpty() && this.subtitle.isEmpty() && this.credits.isEmpty() && this.category.isEmpty();
    }

    public record CreditEntry(String name, String author, Optional<String> website, String license) {
        public static final Codec<CreditEntry> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(CreditEntry::name),
                Codec.STRING.fieldOf("author").forGetter(CreditEntry::author),
                Codec.STRING.optionalFieldOf("website").forGetter(CreditEntry::website),
                Codec.STRING.fieldOf("license").forGetter(CreditEntry::license))
                .apply(instance, CreditEntry::new));
    }
}