package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public record SoundMetadataConfig(Optional<String> title, Optional<String> caption, List<String> credits) {

    public static Codec<SoundMetadataConfig> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(info -> info.title),
            Codec.STRING.optionalFieldOf("caption").forGetter(info -> info.caption),
            Codec.list(Codec.STRING).optionalFieldOf("credits", ImmutableList.of()).forGetter(info -> info.credits))
            .apply(instance, SoundMetadataConfig::new));

    /**
     * Indicates whether the settings in the instance are the default settings.
     *
     * @return true if the properties are the same as defaults; false otherwise
     */
    public boolean isDefault() {
        return this.title.isEmpty() && this.caption.isEmpty() && this.credits.isEmpty();
    }
}