package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class ItemTags {

    static final Collection<TagKey<Item>> TAGS = new HashSet<>();

    public static final TagKey<Item> ENTITY_WATER_BUCKETS = of("entity_water_buckets");
    public static final TagKey<Item> LAVA_BUCKETS = of("lava_buckets");
    public static final TagKey<Item> MILK_BUCKETS = of("milk_buckets");
    public static final TagKey<Item> WATER_BUCKETS = of("water_buckets");

    private static TagKey<Item> of(String id) {
        var tagKey = TagKey.create(Registries.ITEM, new ResourceLocation(Constants.MOD_ID, id));
        TAGS.add(tagKey);
        return tagKey;
    }

}
