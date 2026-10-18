package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.Set;

import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits from what a biome's definition file says it grows (see {@link BiomeDefinitions}). Set by vanilla's: its
 * forests place 6 to 50 trees a chunk (the wooded badlands, sparse, 5), its plains, savannas and sparse jungle under
 * 2.5; oceans and rivers place a tree now and then but have no vegetation to speak of, as swamps' few trees don't make
 * them sparse.
 * <ul>
 *     <li>{@value #FOREST_TREES} or more trees a chunk: a forest, with dense vegetation</li>
 *     <li>some trees, fewer than {@value #SPARSE_TREES}, and not water: sparse vegetation</li>
 * </ul>
 * In between, and a biome with no trees at all (a desert, a beach, the Nether), it says nothing; nor for a cave or
 * underground biome, whose trees are those of the surface above it (vanilla's deep dark places the plains' trees).
 */
public final class BiomeDefinitionAnalyzer implements IBiomeTraitAnalyzer {

    static final float FOREST_TREES = 6F;
    static final float SPARSE_TREES = 2.5F;

    @Override
    public String name() {
        return "BiomeDefinitionAnalyzer";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        if (resultCollection.contains(CAVE) || resultCollection.contains(UNDERGROUND))
            return;
        biome.definition().ifPresent(definition -> addTraits(definition, resultCollection));
    }

    static void addTraits(BiomeDefinition definition, Set<BiomeTrait> results) {
        if (!definition.treesKnown())
            return;
        float trees = definition.treesPerChunk();
        if (trees >= FOREST_TREES) {
            results.add(FOREST);
            results.add(DENSE_VEGETATION);
        } else if (trees > 0F && trees < SPARSE_TREES && !definition.aquatic()) {
            results.add(SPARSE_VEGETATION);
        }
    }
}
