package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.orecruncher.dsurround.Constants;

public class BlockEffectTags {

    public static final TagKey<Block> FIREFLIES = of("fireflies");
    public static final TagKey<Block> FLOOR_SQUEAKS = of("floor_squeaks");
    public static final TagKey<Block> BRUSH_STEP = of("brush_step");
    public static final TagKey<Block> WATERY_STEP = of("watery_step");
    public static final TagKey<Block> STEAM_PRODUCERS = of("steam_producers");
    public static final TagKey<Block> HEAT_PRODUCERS = of("heat_producers");

    private static TagKey<Block> of(String id) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(Constants.MOD_ID, "effects/" + id));
    }
}
