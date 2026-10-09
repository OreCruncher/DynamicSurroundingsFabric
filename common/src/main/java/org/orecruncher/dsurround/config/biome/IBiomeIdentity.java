package org.orecruncher.dsurround.config.biome;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;

/**
 * What biome scripts see of a biome: what it is, not what the configuration gives it. Rule selectors are checked
 * against a {@link BiomeInfoBuilder} while the info is being built, and other scripts against the finished
 * {@link BiomeInfo}.
 */
public interface IBiomeIdentity {

    ResourceLocation getBiomeId();

    String getBiomeName();

    float getDownfall();

    BiomeTraits getTraits();

    default boolean hasTrait(BiomeTrait trait) {
        return this.getTraits().contains(trait);
    }
}
