// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ArmorFamily;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.impl.ItemBehavior.Classification;
import org.orecruncher.dsurround.config.libraries.impl.ItemLibrary.FamilyResult;
import org.orecruncher.dsurround.config.libraries.impl.ItemLibrary.SoundResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The item dump, for checking how a pack's items were sorted: a summary, then each item with why it got what it got.
 */
final class ItemAudit {

    private static final String VANILLA = "minecraft";
    // An elytra is made of no material a family stands for: a mod's, as vanilla's, keeps the elytra's sound
    private static final Identifier ELYTRA = SoundEvents.ARMOR_EQUIP_ELYTRA.value().location();

    private ItemAudit() {
    }

    /**
     * What the item library made of one item, and why.
     *
     * @param classified what decided its class
     * @param byTag      the class our tags give it, or null for none
     * @param byBehavior the class what it does gives it, whatever the tags say
     * @param wornArmor  whether it is worn in an armor slot (head, chest, legs or feet)
     * @param toolTags   the {@code c:tools/...} tags it is in
     */
    record Report(Identifier id, String tags, Classification classified, @Nullable Classification byTag,
                  Classification byBehavior, FamilyResult family, boolean wornArmor, List<Identifier> toolTags,
                  SoundResult toolbar, SoundResult swing, SoundResult step) {
    }

    /**
     * What about an item needs a look from a pack author. Vanilla's items are left out: the rules were written for
     * them, and what they do is known.
     */
    static List<String> concerns(Report report) {
        var result = new ArrayList<String>();
        if (report.id().getNamespace().equals(VANILLA))
            return result;

        // Only a vanilla equip sound no family claims (a turtle shell's, the generic one): a mod's own is played
        var equipSound = report.family().equipSound();
        if (report.wornArmor() && report.family().family() == null
                && equipSound != null && equipSound.getNamespace().equals(VANILLA) && !equipSound.equals(ELYTRA))
            result.add("worn as armor, but in no armor family (" + report.family().reason()
                    + "): tag it with one of " + familyTags());
        if (report.classified().type() == ItemClassType.NONE && !report.toolTags().isEmpty())
            result.add("in " + report.toolTags().stream().map(t -> "#" + t).collect(Collectors.joining(", "))
                    + ", but has no class");
        var byTag = report.byTag();
        var byBehavior = report.byBehavior();
        if (byTag != null && byBehavior.type() != ItemClassType.NONE && byBehavior.type() != byTag.type()
                && !stillTheWeapon(byTag.type(), byBehavior.type()))
            result.add(byTag.reason() + " makes it a " + byTag.type().getName() + ", but it acts as a "
                    + byBehavior.type().getName() + " (" + byBehavior.reason() + ")");
        return result;
    }

    /**
     * Still the weapon it is tagged as, so no disagreement: a melee weapon raised to parry, as a mod's two-handed
     * swords and polearms are (Epic Knights' are blocked with, as a shield is), or a spear charged as a bow is drawn
     * (Epic Knights' lances).
     */
    private static boolean stillTheWeapon(ItemClassType tagged, ItemClassType acts) {
        if (acts == ItemClassType.SHIELD)
            return switch (tagged) {
                case SWORD, AXE, MACE, SPEAR -> true;
                default -> false;
            };
        return tagged == ItemClassType.SPEAR && acts == ItemClassType.BOW;
    }

    private static String familyTags() {
        return Stream.of(ArmorFamily.values()).map(f -> "#" + f.tag().location()).collect(Collectors.joining(", "));
    }

    static Stream<String> format(@Nullable String namespace, List<Report> reports) {
        var lines = new ArrayList<String>();
        lines.add("Items: " + reports.size() + (namespace == null ? "" : " in namespace " + namespace));

        var byClass = new EnumMap<ItemClassType, Integer>(ItemClassType.class);
        var byFamily = new EnumMap<ArmorFamily, Integer>(ArmorFamily.class);
        int wornInNone = 0;
        for (var report : reports) {
            byClass.merge(report.classified().type(), 1, Integer::sum);
            var family = report.family().family();
            if (family != null)
                byFamily.merge(family, 1, Integer::sum);
            else if (report.wornArmor())
                wornInNone++;
        }
        lines.add("By class: " + counts(byClass, ItemClassType::getName));
        lines.add("By armor family: " + counts(byFamily, ArmorFamily::getName)
                + (byFamily.isEmpty() ? "" : ", ") + "worn as armor in none " + wornInNone);

        var needsALook = new ArrayList<String>();
        for (var report : reports)
            for (var concern : concerns(report))
                needsALook.add("  " + report.id() + ": " + concern);
        lines.add("Needs a look (vanilla's items left out): " + needsALook.size());
        lines.addAll(needsALook);
        lines.add("");

        for (var report : reports)
            lines.add(item(report));
        return lines.stream();
    }

    private static <E extends Enum<E>> String counts(Map<E, Integer> counts, Function<E, String> name) {
        return counts.entrySet().stream()
                .map(e -> name.apply(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String item(Report report) {
        var family = report.family();
        return report.id()
                + "\nTags: " + report.tags()
                + "\nClass: " + report.classified().type().getName() + " (" + report.classified().reason() + ")"
                + "\nArmor family: " + (family.family() == null ? "none" : family.family().getName()) + " (" + family.reason() + ")"
                + "\nToolbar sound: " + describe(report.toolbar())
                + "\nSwing sound: " + describe(report.swing())
                + "\nArmor step sound: " + describe(report.step())
                + "\n";
    }

    private static String describe(SoundResult sound) {
        var factory = sound.factory();
        return (factory == null ? "none" : factory.getLocation().toString()) + " (" + sound.source() + ")";
    }
}
