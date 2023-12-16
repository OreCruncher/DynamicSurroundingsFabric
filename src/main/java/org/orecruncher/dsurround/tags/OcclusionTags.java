package org.orecruncher.dsurround.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;

public final class OcclusionTags {

    public static final TagKey<Block> NONE = of("none");
    public static final TagKey<Block> VERY_LOW = of("very_low");
    public static final TagKey<Block> LOW = of("low");
    public static final TagKey<Block> MEDIUM = of("medium");
    public static final TagKey<Block> HIGH = of("high");
    public static final TagKey<Block> VERY_HIGH = of("very_high");
    public static final TagKey<Block> MAX = of("max");

    private static TagKey<Block> of(String id) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier(Client.ModId, "occlusion/" + id));
    }
}