// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.ArmorFamily;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.impl.ItemBehavior.Classification;
import org.orecruncher.dsurround.config.libraries.impl.ItemLibrary.FamilyResult;
import org.orecruncher.dsurround.config.libraries.impl.ItemLibrary.SoundResult;
import org.orecruncher.dsurround.testing.ItemComponents;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the item dump: the reasons it gives and what it says needs a look.
 */
public class ItemAuditTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemComponents.bind();
    }

    private static final SoundResult NO_SOUND = new SoundResult(null, "none");
    private static final FamilyResult NOT_WORN = new FamilyResult(null, "not worn", null);

    private static ItemAudit.Report report(String id, Classification classified, @Nullable Classification byTag,
                                           Classification byBehavior, FamilyResult family, boolean wornArmor,
                                           List<Identifier> toolTags) {
        return new ItemAudit.Report(Identifier.parse(id), "", classified, byTag, byBehavior, family, wornArmor, toolTags,
                NO_SOUND, NO_SOUND, NO_SOUND);
    }

    private static Classification as(ItemClassType type, String reason) {
        return new Classification(type, reason);
    }

    @Test
    void behaviorSaysWhy() {
        assertEquals("drawn like a bow", ItemBehavior.explain(new ItemStack(Items.BOW)).reason());
        assertEquals("its tool mines #minecraft:mineable/pickaxe", ItemBehavior.explain(new ItemStack(Items.IRON_PICKAXE)).reason());
        assertEquals("its tool mines #minecraft:mineable/shovel", ItemBehavior.explain(new ItemStack(Items.IRON_SHOVEL)).reason());
        assertEquals("a weapon", ItemBehavior.explain(new ItemStack(Items.IRON_SWORD)).reason());
        assertSame(Classification.NONE, ItemBehavior.explain(new ItemStack(Items.STICK)));
    }

    @Test
    void armorInNoFamilyNeedsALook() {
        var bronze = report("mymod:bronze_chestplate", Classification.NONE, null, Classification.NONE,
                new FamilyResult(null, "equip sound minecraft:item.armor.equip_turtle is in no family", Identifier.parse("minecraft:item.armor.equip_turtle")), true, List.of());
        var concerns = ItemAudit.concerns(bronze);
        assertEquals(1, concerns.size());
        assertTrue(concerns.getFirst().contains("minecraft:item.armor.equip_turtle"), concerns.getFirst());
        assertTrue(concerns.getFirst().contains("#dsurround:effects/is_plate_armor"), concerns.getFirst());

        var plate = report("mymod:steel_chestplate", Classification.NONE, null, Classification.NONE,
                new FamilyResult(ArmorFamily.PLATE, "equip sound minecraft:item.armor.equip_iron", Identifier.parse("minecraft:item.armor.equip_iron")), true, List.of());
        assertTrue(ItemAudit.concerns(plate).isEmpty());

        // A mod's elytra keeps the elytra's sound, as vanilla's does
        var elytra = report("mymod:ender_elytra", Classification.NONE, null, Classification.NONE,
                new FamilyResult(null, "equip sound minecraft:item.armor.equip_elytra is in no family", Identifier.parse("minecraft:item.armor.equip_elytra")), true, List.of());
        assertTrue(ItemAudit.concerns(elytra).isEmpty());

        // A mod's own equip sound is played, so nothing to tag
        var own = report("mymod:wizard_robe", Classification.NONE, null, Classification.NONE,
                new FamilyResult(null, "equip sound mymod:robe_equip is the mod's own, played instead", Identifier.parse("mymod:robe_equip")), true, List.of());
        assertTrue(ItemAudit.concerns(own).isEmpty());

        // Only what a player wears in an armor slot: a horse's armor makes no steps of ours
        var horse = report("mymod:bronze_horse_armor", Classification.NONE, null, Classification.NONE,
                new FamilyResult(null, "equip sound minecraft:item.armor.equip_turtle is in no family", Identifier.parse("minecraft:item.armor.equip_turtle")), false, List.of());
        assertTrue(ItemAudit.concerns(horse).isEmpty());
    }

    @Test
    void aConventionToolWithNoClassNeedsALook() {
        var shears = report("mymod:bronze_shears", Classification.NONE, null, Classification.NONE, NOT_WORN, false,
                List.of(Identifier.parse("c:tools/shear")));
        assertEquals(List.of("in #c:tools/shear, but has no class"), ItemAudit.concerns(shears));

        var classed = report("mymod:bronze_shovel", as(ItemClassType.TOOL, "behavior"), null, as(ItemClassType.TOOL, "x"),
                NOT_WORN, false, List.of(Identifier.parse("c:tools/shovel")));
        assertTrue(ItemAudit.concerns(classed).isEmpty());
    }

    @Test
    void aTagThatDisagreesWithBehaviorNeedsALook() {
        var tag = as(ItemClassType.SWORD, "tag #dsurround:effects/is_sword");
        var odd = report("mymod:halberd", tag, tag, as(ItemClassType.AXE, "its tool mines #minecraft:mineable/axe"),
                NOT_WORN, false, List.of());
        var concerns = ItemAudit.concerns(odd);
        assertEquals(1, concerns.size());
        assertTrue(concerns.getFirst().contains("sword") && concerns.getFirst().contains("axe"), concerns.getFirst());

        // A sword raised to parry (Epic Knights' two-handed swords) is still a sword; one drawn like a bow needs a look
        var parrying = report("mymod:zweihander", tag, tag, as(ItemClassType.SHIELD, "raised like a shield"), NOT_WORN, false, List.of());
        assertTrue(ItemAudit.concerns(parrying).isEmpty());
        var drawn = report("mymod:lance", tag, tag, as(ItemClassType.BOW, "drawn like a bow"), NOT_WORN, false, List.of());
        assertEquals(1, ItemAudit.concerns(drawn).size());
        // Tagged a spear, the lance charged as a bow is drawn is still a spear
        var spear = as(ItemClassType.SPEAR, "tag #dsurround:effects/is_spear");
        assertTrue(ItemAudit.concerns(report("mymod:lance", spear, spear, as(ItemClassType.BOW, "drawn like a bow"), NOT_WORN, false, List.of())).isEmpty());

        // Behavior that says nothing doesn't disagree
        var book = as(ItemClassType.BOOK, "tag #dsurround:effects/is_book");
        assertTrue(ItemAudit.concerns(report("mymod:tome", book, book, Classification.NONE, NOT_WORN, false, List.of())).isEmpty());
    }

    @Test
    void vanillaItemsAreLeftOut() {
        var tag = as(ItemClassType.MACE, "tag #dsurround:effects/is_mace");
        var mace = report("minecraft:mace", tag, tag, as(ItemClassType.SWORD, "a weapon"), NOT_WORN, false, List.of());
        assertTrue(ItemAudit.concerns(mace).isEmpty());
    }

    @Test
    void theSummaryCountsAndListsWhatNeedsALook() {
        var reports = List.of(
                report("mymod:bronze_chestplate", Classification.NONE, null, Classification.NONE,
                        new FamilyResult(null, "equip sound minecraft:item.armor.equip_turtle is in no family", Identifier.parse("minecraft:item.armor.equip_turtle")), true, List.of()),
                report("mymod:steel_boots", Classification.NONE, null, Classification.NONE,
                        new FamilyResult(ArmorFamily.PLATE, "equip sound minecraft:item.armor.equip_iron", Identifier.parse("minecraft:item.armor.equip_iron")), true, List.of()),
                report("mymod:bronze_axe", as(ItemClassType.AXE, "tag"), null, Classification.NONE, NOT_WORN, false, List.of()));
        var lines = ItemAudit.format("mymod", reports).toList();
        assertEquals("Items: 3 in namespace mymod", lines.get(0));
        assertEquals("By class: none 2, axe 1", lines.get(1));
        assertEquals("By armor family: plate 1, worn as armor in none 1", lines.get(2));
        assertEquals("Needs a look (vanilla's items left out): 1", lines.get(3));
        assertTrue(lines.get(4).startsWith("  mymod:bronze_chestplate: worn as armor"), lines.get(4));
        assertTrue(lines.stream().anyMatch(l -> l.contains("\nArmor family: plate (equip sound minecraft:item.armor.equip_iron)")));
    }
}
