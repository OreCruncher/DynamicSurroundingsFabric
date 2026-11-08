// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.testing.ItemComponents;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for telling what kind of item a stack is by what it does, for items no tag claims.
 */
public class ItemBehaviorTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemComponents.bind();
    }

    // A stick given one component of another item: a modded item that does what that item does, with no tags
    private static <T> ItemStack stickWith(DataComponentType<T> type, Item from) {
        var stack = new ItemStack(Items.STICK);
        var value = new ItemStack(from).get(type);
        assertNotNull(value, from + " has no " + type);
        stack.set(type, value);
        return stack;
    }

    private static ItemClassType of(Item item) {
        return ItemBehavior.classify(new ItemStack(item));
    }

    @Test
    void vanillaItemsAreKnownByWhatTheyDo() {
        // Without any tags: the fallback alone gets vanilla's own right
        assertEquals(ItemClassType.BOW, of(Items.BOW));
        assertEquals(ItemClassType.CROSSBOW, of(Items.CROSSBOW));
        assertEquals(ItemClassType.SHIELD, of(Items.SHIELD));
        assertEquals(ItemClassType.SPEAR, of(Items.TRIDENT));
        assertEquals(ItemClassType.SPEAR, of(Items.IRON_SPEAR));
        assertEquals(ItemClassType.POTION, of(Items.POTION));
        assertEquals(ItemClassType.AXE, of(Items.IRON_AXE));
        assertEquals(ItemClassType.PICKAXE, of(Items.IRON_PICKAXE));
        assertEquals(ItemClassType.TOOL, of(Items.IRON_SHOVEL));
        assertEquals(ItemClassType.TOOL, of(Items.IRON_HOE));
        assertEquals(ItemClassType.SWORD, of(Items.IRON_SWORD), "a sword's own tool, for cobwebs, doesn't make it a tool");
    }

    @Test
    void everydayItemsAreNothing() {
        assertEquals(ItemClassType.NONE, of(Items.STICK));
        assertEquals(ItemClassType.NONE, of(Items.BREAD));
        assertEquals(ItemClassType.NONE, of(Items.MILK_BUCKET), "a drink without a potion's contents isn't a potion");
        assertEquals(ItemClassType.NONE, of(Items.IRON_CHESTPLATE));
        assertEquals(ItemClassType.NONE, ItemBehavior.classify(ItemStack.EMPTY));
    }

    @Test
    void anUntaggedModdedItemIsKnownByItsComponents() {
        assertEquals(ItemClassType.PICKAXE, ItemBehavior.classify(stickWith(DataComponents.TOOL, Items.DIAMOND_PICKAXE)));
        assertEquals(ItemClassType.AXE, ItemBehavior.classify(stickWith(DataComponents.TOOL, Items.DIAMOND_AXE)));
        assertEquals(ItemClassType.TOOL, ItemBehavior.classify(stickWith(DataComponents.TOOL, Items.DIAMOND_SHOVEL)));
        assertEquals(ItemClassType.SWORD, ItemBehavior.classify(stickWith(DataComponents.WEAPON, Items.DIAMOND_SWORD)));
        assertEquals(ItemClassType.SHIELD, ItemBehavior.classify(stickWith(DataComponents.BLOCKS_ATTACKS, Items.SHIELD)));
        assertEquals(ItemClassType.CROSSBOW, ItemBehavior.classify(stickWith(DataComponents.CHARGED_PROJECTILES, Items.CROSSBOW)));
        assertEquals(ItemClassType.SPEAR, ItemBehavior.classify(stickWith(DataComponents.KINETIC_WEAPON, Items.IRON_SPEAR)));
    }

    @Test
    void aWeaponThatAlsoChopsIsAnAxe() {
        // Vanilla's axes are weapons too: what the tool mines decides first
        var stack = stickWith(DataComponents.TOOL, Items.IRON_AXE);
        stack.set(DataComponents.WEAPON, new ItemStack(Items.IRON_AXE).get(DataComponents.WEAPON));
        assertEquals(ItemClassType.AXE, ItemBehavior.classify(stack));
    }

    @Test
    void aToolMinesTheBlocksItsRulesName() {
        var pickaxe = new ItemStack(Items.IRON_PICKAXE).get(DataComponents.TOOL);
        assertNotNull(pickaxe);
        assertTrue(ItemBehavior.mines(pickaxe, BlockTags.MINEABLE_WITH_PICKAXE));
        assertFalse(ItemBehavior.mines(pickaxe, BlockTags.MINEABLE_WITH_AXE));
    }
}
