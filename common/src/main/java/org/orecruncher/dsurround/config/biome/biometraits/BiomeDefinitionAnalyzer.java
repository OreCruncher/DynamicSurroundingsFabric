package org.orecruncher.dsurround.config.biome.biometraits;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;

import java.util.EnumSet;
import java.util.Set;

import static org.orecruncher.dsurround.config.BiomeTrait.*;

/**
 * Traits from what a biome's definition file says it grows (see {@link BiomeDefinitions}). Set by vanilla's: its
 * forests place 6 to 50 trees a chunk (the wooded badlands, sparse, 5), its plains, savannas and sparse jungle under
 * 2.5.
 * <ul>
 *     <li>{@value #FOREST_TREES} or more trees a chunk: dense vegetation, and a forest unless it is water</li>
 *     <li>some trees, fewer than {@value #SPARSE_TREES}, and not water: sparse vegetation</li>
 * </ul>
 * In between, and a biome with no trees at all (a desert, a beach, the Nether), it says nothing. Water (swamps,
 * wetlands) with many trees is dense but not a forest; with few, its trees aren't what grows there. An ocean, river,
 * cave or underground biome is guessed again without them ({@link BiomeTraits}): an ocean's trees are placed where the
 * water is shallow enough, so most of its count never grows (Regions Unexplored's rocky reef counts 39), and a cave's
 * are those of the surface above it (vanilla's deep dark places the plains' trees).
 * <p>
 * Run twice: first for the vegetation, ahead of the other guesses, as the trees say how dense it is better than its
 * music; then for the kind, after them, as a biome with many trees that plays swamp music or is named a bog is a swamp
 * (vanilla's mangrove swamp places more trees than most forests).
 */
public final class BiomeDefinitionAnalyzer implements IBiomeTraitAnalyzer {

    static final float FOREST_TREES = 6F;
    static final float SPARSE_TREES = 2.5F;

    private final boolean kind;

    private BiomeDefinitionAnalyzer(boolean kind) {
        this.kind = kind;
    }

    /**
     * Gives the vegetation: dense or sparse
     */
    public static BiomeDefinitionAnalyzer vegetation() {
        return new BiomeDefinitionAnalyzer(false);
    }

    /**
     * Gives the kind: a forest
     */
    public static BiomeDefinitionAnalyzer kind() {
        return new BiomeDefinitionAnalyzer(true);
    }

    @Override
    public String name() {
        return this.kind ? "BiomeDefinitionAnalyzer (kind)" : "BiomeDefinitionAnalyzer (vegetation)";
    }

    @Override
    public void analyze(@NotNull BiomeEvidence biome, @NotNull Set<BiomeTrait> resultCollection) {
        biome.definition().ifPresent(definition -> {
            var traits = traits(definition);
            if (this.kind)
                traits.retainAll(TraitGroup.KIND.members());
            else
                traits.removeAll(TraitGroup.KIND.members());
            resultCollection.addAll(traits);
        });
    }

    /**
     * Both passes' traits
     */
    static Set<BiomeTrait> traits(BiomeDefinition definition) {
        var results = EnumSet.noneOf(BiomeTrait.class);
        if (!definition.treesKnown())
            return results;
        float trees = definition.treesPerChunk();
        if (trees >= FOREST_TREES) {
            if (!definition.aquatic())
                results.add(FOREST);
            results.add(DENSE_VEGETATION);
        } else if (trees > 0F && trees < SPARSE_TREES && !definition.aquatic()) {
            results.add(SPARSE_VEGETATION);
        }
        return results;
    }
}
