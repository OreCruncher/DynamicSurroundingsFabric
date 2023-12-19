package org.orecruncher.dsurround.lib.version;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import joptsimple.internal.Strings;
import net.fabricmc.loader.api.Version;

import java.util.Map;
import java.util.Optional;

public class VersionInformation {

    private static final Codec<Map<Version, String>> CODEC_RELEASES = Codec.unboundedMap(VersionData.CODEC, Codec.STRING).stable();
    private static final Codec<Map<Version, Map<Version, String>>> MAJOR_VERSION_RELEASES = Codec.unboundedMap(VersionData.CODEC, CODEC_RELEASES).stable();
    private static final Codec<Map<Version,Version>> RECOMMENDATION = Codec.unboundedMap(VersionData.CODEC, VersionData.CODEC).stable();

    public static Codec<VersionInformation> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.STRING.fieldOf("downloadLocation").forGetter(info -> info.downloadLocation),
                    Codec.STRING.optionalFieldOf("downloadLocationModrinth", "").forGetter(info -> info.downloadLocationModrinth),
                    MAJOR_VERSION_RELEASES.fieldOf("releases").forGetter(info -> info.releases),
                    RECOMMENDATION.fieldOf("recommend").forGetter(info -> info.recommended)
            ).apply(instance, VersionInformation::new));

    public final String downloadLocation;
    public final String downloadLocationModrinth;
    public final Map<Version, Map<Version, String>> releases;
    public final Map<Version, Version> recommended;

    VersionInformation(String downloadLocation, String downloadLocationModrinth, Map<Version, Map<Version, String>> releases, Map<Version, Version> recommended) {
        this.downloadLocation = downloadLocation;
        this.downloadLocationModrinth = downloadLocationModrinth;
        this.releases = releases;
        this.recommended = recommended;
    }

    /**
     * Gets the newest release as compared to the current version information provided.
     * @param minecraftVersion  Version of Minecraft installed
     * @param modVersion        Mod version installed
     * @return Pair containing the newest version and associated information
     */
    public Optional<Pair<Version, String>> getNewestVersion(Version minecraftVersion, Version modVersion) {

        var recommendation = this.recommended.get(minecraftVersion);
        if (recommendation == null)
            return Optional.empty();

        if (modVersion.compareTo(recommendation) >= 0) {
            String notes = null;
            var releases = this.releases.get(minecraftVersion);
            if (releases != null) {
                notes = releases.get(recommendation);
                if (notes == null)
                    notes = Strings.EMPTY;
            }
            return Optional.of(Pair.of(recommendation, notes));
        }
        return Optional.empty();
    }

}
