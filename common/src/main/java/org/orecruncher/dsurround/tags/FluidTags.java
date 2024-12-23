package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class FluidTags {

    static final Collection<TagKey<Fluid>> TAGS = new HashSet<>();

    public static final TagKey<Fluid> WATER_RIPPLES = of("water_ripples");
    public static final TagKey<Fluid> WATERFALL_SOURCE = of("waterfall_sources");
    public static final TagKey<Fluid> WATERFALL_SOUND = of("waterfall_sounds");

    private static TagKey<Fluid> of(String id) {
        var tagKey = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }
}
