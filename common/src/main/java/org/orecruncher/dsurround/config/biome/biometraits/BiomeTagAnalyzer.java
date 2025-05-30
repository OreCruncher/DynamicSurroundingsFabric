package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

import java.util.*;

public class BiomeTagAnalyzer implements IBiomeTraitAnalyzer {

    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final Map<TagKey<Biome>, BiomeTrait> tagToTraitMap = new HashMap<>();

    static {

        for (var entry : BiomeTrait.values())
            tagToTraitMap.put(entry.getBiomeTag(), entry);
    }

    @Override
    public Collection<BiomeTrait> evaluate(ResourceLocation id, Biome biome) {
        Set<BiomeTrait> results = new HashSet<>();

        // Have to do it this way so that the client side tagging has a chance.  When connecting to
        // vanilla servers, they will ONLY have the Minecraft tags, not the Fabric ones.
        for (var tagEntry : tagToTraitMap.entrySet())
            if (TAG_LIBRARY.is(tagEntry.getKey(), biome))
                results.add(tagEntry.getValue());

        // Add additional tags for completeness
        if (results.contains(BiomeTrait.CAVE))
            results.add(BiomeTrait.UNDERGROUND);

        return results;
    }
}
