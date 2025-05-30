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

    public static final TagKey<Item> AXES = of("is_axe");
    public static final TagKey<Item> BOOKS = of("is_book");
    public static final TagKey<Item> BOWS = of("is_bow");
    public static final TagKey<Item> CROSSBOWS = of("is_crossbow");
    public static final TagKey<Item> MACES = of("is_mace");
    public static final TagKey<Item> POTIONS = of("is_potion");
    public static final TagKey<Item> SHIELDS = of("is_shield");
    public static final TagKey<Item> SPEARS = of("is_spear");
    public static final TagKey<Item> SWORDS = of("is_sword");
    public static final TagKey<Item> TOOLS = of("is_tool");
    public static final TagKey<Item> COMPASSES = of("is_compass");
    public static final TagKey<Item> COMPASS_WOBBLE = of("compass_wobble");
    public static final TagKey<Item> CLOCKS = of("is_clock");

    private static TagKey<Item> of(String id) {
        var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }

}
