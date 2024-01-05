package org.orecruncher.dsurround.lib.version;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import joptsimple.internal.Strings;

import java.util.Map;
import java.util.Optional;

public record VersionInformation(Map<SemanticVersion, Map<SemanticVersion, String>> releases, Map<SemanticVersion, SemanticVersion> recommended) {

    private static final Codec<Map<SemanticVersion, String>> CODEC_RELEASES = Codec.unboundedMap(SemanticVersion.CODEC, Codec.STRING).stable();
    private static final Codec<Map<SemanticVersion, Map<SemanticVersion, String>>> MAJOR_VERSION_RELEASES = Codec.unboundedMap(SemanticVersion.CODEC, CODEC_RELEASES).stable();
    private static final Codec<Map<SemanticVersion, SemanticVersion>> RECOMMENDATION = Codec.unboundedMap(SemanticVersion.CODEC, SemanticVersion.CODEC).stable();

    public static Codec<VersionInformation> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                MAJOR_VERSION_RELEASES.fieldOf("releases").forGetter(VersionInformation::releases),
                RECOMMENDATION.fieldOf("recommend").forGetter(VersionInformation::recommended)
            ).apply(instance, VersionInformation::new));

    /**
     * Gets the newest release as compared to the current version information provided.
     * @param minecraftVersion  Version of Minecraft installed
     * @param modVersion        Mod version installed
     * @return Pair containing the newest version and associated information
     */
    public Optional<Pair<SemanticVersion, String>> getNewestVersion(SemanticVersion minecraftVersion, SemanticVersion modVersion) {

        var recommendation = this.recommended.get(minecraftVersion);
        if (recommendation == null)
            return Optional.empty();

        if (modVersion.compareTo(recommendation) < 0) {
            String releaseNotes = Strings.EMPTY;
            var releases = this.releases.get(minecraftVersion);
            if (releases != null) {
                releaseNotes = releases.get(recommendation);
                if (releaseNotes == null)
                    releaseNotes = Strings.EMPTY;
            }
            return Optional.of(Pair.of(recommendation, releaseNotes));
        }
        return Optional.empty();
    }
}
