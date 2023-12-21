package org.orecruncher.dsurround.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;

public class BlockEffectTags {

    public static final TagKey<Block> FIREFLIES = of("fireflies");

    private static TagKey<Block> of(String id) {
        return TagKey.of(RegistryKeys.BLOCK, new Identifier(Client.ModId, "effects/" + id));
    }
}
