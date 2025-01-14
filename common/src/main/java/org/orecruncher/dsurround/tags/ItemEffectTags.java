package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class ItemEffectTags {

    static final Collection<TagKey<Item>> TAGS = new HashSet<>();

    public static final TagKey<Item> AXES = of("axes");
    public static final TagKey<Item> BOOKS = of("books");
    public static final TagKey<Item> BOWS = of("bows");
    public static final TagKey<Item> CROSSBOWS = of("crossbows");
    public static final TagKey<Item> POTIONS = of("potions");
    public static final TagKey<Item> SHIELDS = of("shields");
    public static final TagKey<Item> SWORDS = of("swords");
    public static final TagKey<Item> TOOLS = of("tools");
    public static final TagKey<Item> COMPASSES = of("compasses");
    public static final TagKey<Item> COMPASS_WOBBLE = of("compass_wobble");
    public static final TagKey<Item> CLOCKS = of("clocks");

    private static TagKey<Item> of(String id) {
        var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }

}
