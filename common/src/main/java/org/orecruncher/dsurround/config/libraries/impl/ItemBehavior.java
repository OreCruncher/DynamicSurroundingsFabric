// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import org.orecruncher.dsurround.config.ItemClassType;

/**
 * What kind of item a stack is by what it does, for items no tag of ours claims: a modded weapon or tool its author
 * never tagged. Since 26.x an item's behavior is in its components, which a mod has to set for the item to work at
 * all, so they say what it is more surely than its name or class: how it is used (drawn like a bow, raised like a
 * shield), whether it blocks attacks or is charged with arrows, what its tool mines, whether it is a weapon. Tags come
 * first (ItemLibrary), so a pack can still put any item where it likes.
 */
final class ItemBehavior {

    private ItemBehavior() {
    }

    static ItemClassType classify(ItemStack stack) {
        if (stack.isEmpty())
            return ItemClassType.NONE;

        switch (stack.getUseAnimation()) {
            case BOW -> {
                return ItemClassType.BOW;
            }
            case CROSSBOW -> {
                return ItemClassType.CROSSBOW;
            }
            case BLOCK -> {
                return ItemClassType.SHIELD;
            }
            case SPEAR, TRIDENT -> {
                return ItemClassType.SPEAR;
            }
            case DRINK -> {
                // A drink is a potion only with a potion's contents: milk and honey aren't
                if (stack.has(DataComponents.POTION_CONTENTS))
                    return ItemClassType.POTION;
            }
            default -> {
            }
        }

        if (stack.has(DataComponents.CHARGED_PROJECTILES))
            return ItemClassType.CROSSBOW;
        if (stack.has(DataComponents.BLOCKS_ATTACKS))
            return ItemClassType.SHIELD;
        if (stack.has(DataComponents.KINETIC_WEAPON) || stack.has(DataComponents.PIERCING_WEAPON))
            return ItemClassType.SPEAR;

        // A sword has a tool too, for cobwebs and the like: only what a tool mines in quantity says what it is
        var tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            if (mines(tool, BlockTags.MINEABLE_WITH_AXE))
                return ItemClassType.AXE;
            if (mines(tool, BlockTags.MINEABLE_WITH_PICKAXE))
                return ItemClassType.PICKAXE;
            if (mines(tool, BlockTags.MINEABLE_WITH_SHOVEL) || mines(tool, BlockTags.MINEABLE_WITH_HOE))
                return ItemClassType.TOOL;
        }

        if (stack.has(DataComponents.WEAPON))
            return ItemClassType.SWORD;

        return ItemClassType.NONE;
    }

    /**
     * Whether a tool has a rule for the blocks of a tag (vanilla's tools name their mineable tag), whatever the rule
     * does with them
     */
    static boolean mines(Tool tool, TagKey<Block> blocks) {
        for (var rule : tool.rules())
            if (rule.blocks().unwrapKey().filter(blocks::equals).isPresent())
                return true;
        return false;
    }
}
