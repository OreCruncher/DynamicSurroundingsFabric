// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class ItemEffectTags {

    static final Collection<TagKey<Item>> TAGS = new HashSet<>();

    public static final TagKey<Item> AXES = of("is_axe");
    public static final TagKey<Item> PICKAXES = of("is_pickaxe");
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
    public static final TagKey<Item> CLOCK_WOBBLE = of("clock_wobble");
    // Armor families (config/ArmorFamily): empty, for packs to put a piece in a family its equip sound doesn't say
    public static final TagKey<Item> LEATHER_ARMOR = of("is_leather_armor");
    public static final TagKey<Item> CHAIN_ARMOR = of("is_chain_armor");
    public static final TagKey<Item> PLATE_ARMOR = of("is_plate_armor");
    public static final TagKey<Item> CRYSTAL_ARMOR = of("is_crystal_armor");
    public static final TagKey<Item> HEAVY_ARMOR = of("is_heavy_armor");

    private static TagKey<Item> of(String id) {
        var tagKey = TagKey.create(Registries.ITEM, Constants.asId("effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }

}
