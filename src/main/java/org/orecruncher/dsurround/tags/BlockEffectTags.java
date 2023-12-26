package org.orecruncher.dsurround.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Constants;

public class BlockEffectTags {

    public static final TagKey<Block> FIREFLIES = of("fireflies");
    public static final TagKey<Block> FLOOR_SQUEAKS = of("floor_squeaks");
    public static final TagKey<Block> BRUSH_STEP = of("brush_step");
    public static final TagKey<Block> WATERY_STEP = of("watery_step");

    private static TagKey<Block> of(String id) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier(Constants.MOD_ID, "effects/" + id));
    }
}
