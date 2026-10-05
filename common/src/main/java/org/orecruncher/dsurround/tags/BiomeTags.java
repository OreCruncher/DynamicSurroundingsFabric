package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * The mod's biome tags, cached with its other tags when tags load (see {@link ModTags}): one for each biome trait
 * ("is_" and the trait's name), and a few finer ones that aren't traits.
 */
public class BiomeTags {

    // Biome tags that aren't traits, for configuration and resource packs to use
    private static final List<String> FINER = List.of("is_mountain_peak", "is_mountain_slope", "is_tree_jungle", "is_tree_savanna");

    static final Collection<TagKey<Biome>> TAGS = new HashSet<>();

    static {
        for (var trait : BiomeTrait.values())
            TAGS.add(trait.getBiomeTag());
        for (var name : FINER)
            TAGS.add(TagKey.create(Registries.BIOME, Constants.asId(name)));
    }
}
