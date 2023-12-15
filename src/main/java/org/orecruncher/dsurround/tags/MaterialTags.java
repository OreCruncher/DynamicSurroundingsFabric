package org.orecruncher.dsurround.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;

public final class MaterialTags {

    public static final TagKey<Block> PLANT = of("plant");
    public static final TagKey<Block> UNDERWATER_PLANT = of("underwater_plant");
    public static final TagKey<Block> CACTUS = of("cactus");
    public static final TagKey<Block> ORGANIC = of("organic");
    public static final TagKey<Block> METAL = of("metal");
    public static final TagKey<Block> SPONGE = of("sponge");
    public static final TagKey<Block> SOIL = of("soil");
    public static final TagKey<Block> WATER = of("water");
    public static final TagKey<Block> LAVA = of("lava");
    public static final TagKey<Block> STONE = of("stone");
    public static final TagKey<Block> COBBLED_STONE = of("cobbled_stone");
    public static final TagKey<Block> AGGREGATE = of("aggregate");
    public static final TagKey<Block> SNOW_LAYER = of("snow_layer");

    private static TagKey<Block> of(String id) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier(Client.ModId, "materials/" + id));
    }
}