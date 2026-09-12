package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.*;

/**
 * Generates biome traits for a biome based on the biome tags that have been applied by various asset packs.
 */
public final class BiomeTagAnalyzer implements IBiomeTraitAnalyzer {

    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final Map<TagKey<Biome>, BiomeTrait> tagToTraitMap = new HashMap<>();

    static {
        for (var entry : BiomeTrait.values())
            tagToTraitMap.put(entry.getBiomeTag(), entry);
    }

    @Override
    public String name() {
        return "BiomeTagAnalyzer";
    }

    @Override
    public void analyze(@NotNull ResourceLocation id, @NotNull Biome biome, @NotNull Set<BiomeTrait> resultCollection) {
        // Have to do it this way so that the client side tagging has a chance.  When connecting to
        // vanilla servers, they will ONLY have the Minecraft tags, not the Fabric ones.
        for (var tagEntry : tagToTraitMap.entrySet())
            if (TAG_LIBRARY.is(tagEntry.getKey(), biome))
                resultCollection.add(tagEntry.getValue());
    }
}
