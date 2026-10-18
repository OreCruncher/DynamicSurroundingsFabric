package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.EnumSet;
import java.util.Set;

import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits a guess may only add if the biome has none of the group yet: its dimension, how warm it is, how wet, how
 * much grows, and what kind of place it is. Once a biome has one of a group from its tags (or anything else taken as
 * given), a guess can't add another: a modded taiga tagged cold with a temperature of 0.25 stays cold, rather than
 * its climate making it temperate and cleanup taking the cold away; a field tagged plains doesn't become a forest
 * because it plays vanilla's forest music.
 * <p>
 * A biome has one of each group but the kind: it can be a forest on a mountain. Other traits (snowy, floral, rare,
 * coniferous...) describe a biome of any kind, and guesses add to them.
 */
public enum TraitGroup {
    DIMENSION(OVERWORLD, NETHER, END, VOID),
    TEMPERATURE(COLD, TEMPERATE, HOT),
    MOISTURE(DRY, WET),
    VEGETATION(SPARSE_VEGETATION, DENSE_VEGETATION),
    KIND(BADLANDS, BEACH, BIRCH_FOREST, CAVE, DARK_FOREST, DEEP_OCEAN, DESERT, FLOWER_FOREST, FOREST, HILL, JUNGLE,
            MOUNTAIN, MUSHROOM, NETHER_FOREST, OCEAN, OUTER_END_ISLAND, PLAINS, PLATEAU, RIVER, SAVANNA, SHALLOW_OCEAN,
            SNOWY_PLAINS, STONY_SHORES, SWAMP, TAIGA, UNDERGROUND, WASTELAND);

    private final Set<BiomeTrait> members;

    TraitGroup(BiomeTrait first, BiomeTrait... rest) {
        this.members = EnumSet.of(first, rest);
    }

    public Set<BiomeTrait> members() {
        return this.members;
    }

    /**
     * The group a trait is in, or null if it isn't in one
     */
    public static @Nullable TraitGroup of(BiomeTrait trait) {
        for (var group : values())
            if (group.members.contains(trait))
                return group;
        return null;
    }
}
