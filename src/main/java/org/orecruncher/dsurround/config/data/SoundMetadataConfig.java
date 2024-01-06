package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public record SoundMetadataConfig(Optional<String> title, Optional<String> subtitle, List<CreditEntry> credits) {

    public static Codec<SoundMetadataConfig> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(SoundMetadataConfig::title),
            Codec.STRING.optionalFieldOf("subtitle").forGetter(SoundMetadataConfig::subtitle),
            Codec.list(CreditEntry.CODEC).optionalFieldOf("credits", ImmutableList.of()).forGetter(SoundMetadataConfig::credits))
            .apply(instance, SoundMetadataConfig::new));

    /**
     * Indicates whether the settings in the instance are the default settings.
     *
     * @return true if the properties are the same as defaults; false otherwise
     */
    public boolean isDefault() {
        return this.title.isEmpty() && this.subtitle.isEmpty() && this.credits.isEmpty();
    }

    public record CreditEntry(String name, String author, String license) {
        public static Codec<CreditEntry> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(CreditEntry::name),
                Codec.STRING.fieldOf("author").forGetter(CreditEntry::author),
                Codec.STRING.fieldOf("license").forGetter(CreditEntry::license))
                .apply(instance, CreditEntry::new));
    }
}