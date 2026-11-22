// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.ItemComponents;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemLibraryEquipSoundTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemComponents.bind();
    }

    @Test
    void anArmorPiecesEquipSoundIsFound() {
        var sound = ItemLibrary.getEquipableSoundEvent(new ItemStack(Items.IRON_HELMET));
        assertNotNull(sound);
        assertEquals(SoundEvents.ARMOR_EQUIP_IRON.value(), sound);
    }

    @Test
    void anItemNotWornHasNone() {
        assertNull(ItemLibrary.getEquipableSoundEvent(new ItemStack(Items.STICK)));
    }

    @Test
    void anEquippableWithoutAnEquipSoundHasNone() {
        // A mod's component made with a null equip sound (seen in The Cozy Artificer): none, not a crash
        var stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.EQUIPPABLE, new Equippable(EquipmentSlot.HEAD, null, Optional.empty(), Optional.empty(),
                Optional.empty(), true, true, true, false, false, Holder.direct(SoundEvents.SHEARS_SNIP)));
        assertNull(ItemLibrary.getEquipableSoundEvent(stack));
    }
}
