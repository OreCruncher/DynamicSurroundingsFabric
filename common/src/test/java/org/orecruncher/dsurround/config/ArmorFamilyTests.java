// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.sound.SoundFactory;
import org.orecruncher.dsurround.testing.ItemComponents;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for telling a piece of armor's family by its equip sound.
 */
public class ArmorFamilyTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemComponents.bind();
    }

    private static @Nullable ArmorFamily of(Item item) {
        var equippable = new ItemStack(item).get(DataComponents.EQUIPPABLE);
        return equippable == null ? null : ArmorFamily.byEquipSound(equippable.equipSound().value());
    }

    @Test
    void vanillaArmorFallsIntoItsFamily() {
        assertEquals(ArmorFamily.LEATHER, of(Items.LEATHER_BOOTS));
        assertEquals(ArmorFamily.LEATHER, of(Items.LEATHER_CHESTPLATE));
        assertEquals(ArmorFamily.CHAIN, of(Items.CHAINMAIL_LEGGINGS));
        assertEquals(ArmorFamily.PLATE, of(Items.IRON_LEGGINGS));
        assertEquals(ArmorFamily.PLATE, of(Items.GOLDEN_BOOTS));
        assertEquals(ArmorFamily.PLATE, of(Items.COPPER_CHESTPLATE));
        assertEquals(ArmorFamily.CRYSTAL, of(Items.DIAMOND_BOOTS));
        assertEquals(ArmorFamily.HEAVY, of(Items.NETHERITE_LEGGINGS));
    }

    @Test
    void piecesOfNoFamilyKeepTheirOwnSound() {
        assertNull(of(Items.ELYTRA));
        assertNull(of(Items.TURTLE_HELMET));
        assertNull(of(Items.CARVED_PUMPKIN));
        assertNull(ArmorFamily.byEquipSound(null));
    }

    @Test
    void everyFamilyHasItsSoundFactories() throws Exception {
        try (var stream = Objects.requireNonNull(ArmorFamilyTests.class.getResourceAsStream("/assets/dsurround/dsconfigs/sound_factories.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var factories = Codec.list(SoundFactory.CODEC).parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            var locations = factories.stream().map(SoundFactory::getLocation).collect(Collectors.toSet());
            for (var family : ArmorFamily.values()) {
                for (Identifier id : new Identifier[]{family.stepSoundId(), family.equipSoundId()})
                    assertTrue(locations.contains(id), "no factory " + id);
            }
        }
    }
}
