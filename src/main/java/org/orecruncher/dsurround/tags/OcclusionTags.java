package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.orecruncher.dsurround.Constants;

public final class OcclusionTags {

    public static final TagKey<Block> NONE = of("none");
    public static final TagKey<Block> VERY_LOW = of("very_low");
    public static final TagKey<Block> LOW = of("low");
    public static final TagKey<Block> MEDIUM = of("medium");
    public static final TagKey<Block> HIGH = of("high");
    public static final TagKey<Block> VERY_HIGH = of("very_high");
    public static final TagKey<Block> MAX = of("max");

    private static TagKey<Block> of(String id) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(Constants.MOD_ID, "occlusion/" + id));
    }
}