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

    /**
     * What kind of item a stack is, and why: the reason goes into the item dump.
     */
    record Classification(ItemClassType type, String reason) {
        static final Classification NONE = new Classification(ItemClassType.NONE, "nothing it does says");
    }

    static ItemClassType classify(ItemStack stack) {
        return explain(stack).type();
    }

    static Classification explain(ItemStack stack) {
        if (stack.isEmpty())
            return Classification.NONE;

        switch (stack.getUseAnimation()) {
            case BOW -> {
                return new Classification(ItemClassType.BOW, "drawn like a bow");
            }
            case CROSSBOW -> {
                return new Classification(ItemClassType.CROSSBOW, "loaded like a crossbow");
            }
            case BLOCK -> {
                return new Classification(ItemClassType.SHIELD, "raised like a shield");
            }
            case SPEAR, TRIDENT -> {
                return new Classification(ItemClassType.SPEAR, "held like a spear");
            }
            case DRINK -> {
                // A drink is a potion only with a potion's contents: milk and honey aren't
                if (stack.has(DataComponents.POTION_CONTENTS))
                    return new Classification(ItemClassType.POTION, "a drink with potion contents");
            }
            default -> {
            }
        }

        if (stack.has(DataComponents.CHARGED_PROJECTILES))
            return new Classification(ItemClassType.CROSSBOW, "charged with projectiles");
        if (stack.has(DataComponents.BLOCKS_ATTACKS))
            return new Classification(ItemClassType.SHIELD, "blocks attacks");
        if (stack.has(DataComponents.KINETIC_WEAPON) || stack.has(DataComponents.PIERCING_WEAPON))
            return new Classification(ItemClassType.SPEAR, "a kinetic or piercing weapon");

        // A sword has a tool too, for cobwebs and the like: only what a tool mines in quantity says what it is
        var tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            if (mines(tool, BlockTags.MINEABLE_WITH_AXE))
                return minesWith(ItemClassType.AXE, BlockTags.MINEABLE_WITH_AXE);
            if (mines(tool, BlockTags.MINEABLE_WITH_PICKAXE))
                return minesWith(ItemClassType.PICKAXE, BlockTags.MINEABLE_WITH_PICKAXE);
            if (mines(tool, BlockTags.MINEABLE_WITH_SHOVEL))
                return minesWith(ItemClassType.TOOL, BlockTags.MINEABLE_WITH_SHOVEL);
            if (mines(tool, BlockTags.MINEABLE_WITH_HOE))
                return minesWith(ItemClassType.TOOL, BlockTags.MINEABLE_WITH_HOE);
        }

        if (stack.has(DataComponents.WEAPON))
            return new Classification(ItemClassType.SWORD, "a weapon");

        return Classification.NONE;
    }

    private static Classification minesWith(ItemClassType type, TagKey<Block> blocks) {
        return new Classification(type, "its tool mines #" + blocks.location());
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
