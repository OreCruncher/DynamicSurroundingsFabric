package org.orecruncher.dsurround.lib.version;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * The mod's published version information (versions.json): for each Minecraft version, the releases with their
 * release notes links, and the recommended release.
 */
public record VersionInformation(Map<SemanticVersion, Map<SemanticVersion, String>> releases, Map<SemanticVersion, SemanticVersion> recommended) {

    private static final Codec<Map<SemanticVersion, String>> CODEC_RELEASES = Codec.unboundedMap(SemanticVersion.CODEC, Codec.STRING).stable();
    private static final Codec<Map<SemanticVersion, Map<SemanticVersion, String>>> MAJOR_VERSION_RELEASES = Codec.unboundedMap(SemanticVersion.CODEC, CODEC_RELEASES).stable();
    private static final Codec<Map<SemanticVersion, SemanticVersion>> RECOMMENDATION = Codec.unboundedMap(SemanticVersion.CODEC, SemanticVersion.CODEC).stable();

    public static final Codec<VersionInformation> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                MAJOR_VERSION_RELEASES.fieldOf("releases").forGetter(VersionInformation::releases),
                RECOMMENDATION.fieldOf("recommend").forGetter(VersionInformation::recommended)
            ).apply(instance, VersionInformation::new));

    /**
     * The recommended release for a Minecraft version.
     *
     * @param version         the release
     * @param releaseNotesUrl a link to its release notes, or null if there is none
     */
    public record Recommendation(SemanticVersion version, @Nullable String releaseNotesUrl) {
    }

    /**
     * Gets the recommended release for the Minecraft version, with a link to its release notes if versions.json
     * has one. Some older entries have text rather than a link; those are treated as having none.
     *
     * @param minecraftVersion Version of Minecraft installed
     * @return the recommendation, or empty if there is none for this Minecraft version
     */
    public Optional<Recommendation> getRecommendation(SemanticVersion minecraftVersion) {
        var recommendation = this.recommended.get(minecraftVersion);
        if (recommendation == null)
            return Optional.empty();

        var releases = this.releases.get(minecraftVersion);
        var releaseNotes = releases == null ? null : releases.get(recommendation);
        return Optional.of(new Recommendation(recommendation, isWebLink(releaseNotes) ? releaseNotes : null));
    }

    /**
     * Whether {@code text} is an http or https link, which is all a chat link should open.
     */
    static boolean isWebLink(@Nullable String text) {
        if (text == null || text.isBlank())
            return false;
        try {
            var uri = new URI(text);
            var scheme = uri.getScheme();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
