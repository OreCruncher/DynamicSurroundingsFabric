package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits suggested by the words of a biome's id: the last thing looked at, for what nothing else said.
 * <p>
 * A name doesn't say which dimension a biome is in, beyond "nether" and "end": a forest, river or beach may be in
 * the Twilight Forest or the Undergarden as well as the Overworld.
 * <p>
 * A name is split into words at underscores and slashes, and a word must be one looked for, or one with a common
 * ending added ({@link #ENDINGS}): "snowy" and "caves" count, but "ice" isn't found in "dice", "pine" in "alpine" or
 * "pale" in "palette", as they were when names were searched for the letters anywhere.
 */
public final class BiomeNameFallbackAnalyzer implements IBiomeTraitAnalyzer {

    // Endings a word may have past the one looked for: plurals, -y (snowy), -ed (iced), -lands (pinelands)...
    static final List<String> ENDINGS = List.of("", "s", "es", "y", "ies", "ed", "ing", "ous", "land", "lands", "wood", "woods");

    @Override
    public String name() {
        return "BiomeNameFallbackAnalyzer";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        addNameBasedTraits(biome.id().getPath().toLowerCase(Locale.ROOT), resultCollection);
    }

    static void addNameBasedTraits(String path, @NotNull Set<BiomeTrait> results) {
        if (hasWord(path, "nether")) {
            results.add(NETHER);
            results.add(HOT);
            results.add(DRY);
        }

        // Matched exactly: "endless" and "ending" aren't the End
        if (hasExactWord(path, "end")) {
            results.add(END);
            results.add(DRY);
        }

        if (hasWord(path, "cave", "cavern", "underground")) {
            results.add(UNDERGROUND);
            results.add(CAVE);
        }

        if (hasWord(path, "sulfur", "sulphur")) {
            results.add(UNDERGROUND);
            results.add(CAVE);
            results.add(HOT);
            results.add(DRY);
            results.add(WASTELAND);
        }

        if (hasWord(path, "ocean")) {
            results.add(OCEAN);
            results.add(WET);
            if (hasWord(path, "deep")) {
                results.add(DEEP_OCEAN);
            }
        }

        if (hasWord(path, "river")) {
            results.add(RIVER);
            results.add(WET);
        }

        if (hasWord(path, "forest", "wood", "grove")) {
            results.add(FOREST);
            results.add(DENSE_VEGETATION);
        }

        if (hasWord(path, "taiga", "pine", "spruce", "conifer")) {
            results.add(TAIGA);
            results.add(CONIFEROUS);
        }

        if (hasWord(path, "jungle")) {
            results.add(JUNGLE);
            results.add(HOT);
            results.add(WET);
        }

        if (hasWord(path, "swamp", "bog", "marsh")) {
            results.add(SWAMP);
            results.add(WET);
        }

        if (hasWord(path, "sea")) {
            results.add(OCEAN);
            results.add(WET);
        }

        if (hasWord(path, "stream", "lake")) {
            results.add(RIVER);
            results.add(WET);
        }

        // "grassland" whole, not "grass": grassy_beach is a beach
        if (hasWord(path, "field", "plain", "prairie", "grassland", "meadow", "steppe", "clearing", "shrub")) {
            results.add(PLAINS);
        }

        if (hasWord(path, "savanna", "savannah")) {
            results.add(SAVANNA);
        }

        if (hasWord(path, "plateau")) {
            results.add(PLATEAU);
        }

        if (hasWord(path, "highland")) {
            results.add(HILL);
        }

        if (hasWord(path, "tundra")) {
            results.add(PLAINS);
            results.add(SNOWY);
        }

        if (hasWord(path, "glacier")) {
            results.add(SNOWY);
            results.add(ICY);
        }

        if (hasWord(path, "desert")) {
            results.add(DESERT);
            results.add(HOT);
            results.add(DRY);
            results.add(SANDY);
        }

        if (hasWord(path, "badland", "wasteland")) {
            results.add(BADLANDS);
            results.add(WASTELAND);
            results.add(HOT);
            results.add(DRY);
        }

        if (hasWord(path, "beach", "shore")) {
            results.add(BEACH);
            // Vanilla's beach is wet; its stony shore isn't
            if (hasWord(path, "beach"))
                results.add(WET);
        }

        if (hasWord(path, "snow", "frozen", "ice", "icy")) {
            results.add(COLD);
            results.add(SNOWY);
        }

        if (hasWord(path, "peak", "slope", "mountain")) {
            results.add(MOUNTAIN);
        }

        if (hasWord(path, "flower", "meadow", "cherry")) {
            results.add(FLORAL);
        }

        if (hasWord(path, "dark", "ominous", "pale")) {
            results.add(SPOOKY);
        }

        if (hasWord(path, "magic", "magical", "magik")) {
            results.add(MAGICAL);
        }
    }

    /**
     * Whether one of the words of {@code path} (the parts between underscores and slashes) is one of {@code words},
     * possibly with one of the {@link #ENDINGS}
     */
    static boolean hasWord(String path, String... words) {
        for (var part : path.split("[_/]"))
            for (var word : words)
                if (part.startsWith(word) && ENDINGS.contains(part.substring(word.length())))
                    return true;
        return false;
    }

    /**
     * Whether {@code word} is one of the words of {@code path}, as it is
     */
    static boolean hasExactWord(String path, String word) {
        for (var part : path.split("[_/]"))
            if (part.equals(word))
                return true;
        return false;
    }
}
