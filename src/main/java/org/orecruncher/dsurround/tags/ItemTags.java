package org.orecruncher.dsurround.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;

public class ItemTags {

    public static final TagKey<Item> ENTITY_WATER_BUCKETS = of("entity_water_buckets");
    public static final TagKey<Item> LAVA_BUCKETS = of("lava_buckets");
    public static final TagKey<Item> MILK_BUCKETS = of("milk_buckets");
    public static final TagKey<Item> WATER_BUCKETS = of("water_buckets");

    private static TagKey<Item> of(String id) {
        return TagKey.of(RegistryKeys.ITEM, new Identifier(Client.ModId, id));
    }

}
