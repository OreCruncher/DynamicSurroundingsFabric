// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.ItemEffectTags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a piece of armor is made of, for the sounds it makes as its wearer walks and as it is selected in the hotbar.
 * A piece's family is its tag ({@link #tag}), or else the equip sound of its {@code EQUIPPABLE} component: nearly every
 * modded piece reuses one of vanilla's, so a mod's bronze plate that equips with the iron sound clanks like iron with
 * nothing to configure. A piece in no family (an elytra, a turtle shell, an equip sound of a mod's own) keeps its own
 * equip sound for both.
 * <p>
 * Each family's sounds are the factories {@code dsurround:armor.<name>.step} and {@code dsurround:armor.<name>.equip}
 * in {@code sound_factories.json}.
 */
public enum ArmorFamily {
    LEATHER("leather", ItemEffectTags.LEATHER_ARMOR, List.of(SoundEvents.ARMOR_EQUIP_LEATHER)),
    CHAIN("chain", ItemEffectTags.CHAIN_ARMOR, List.of(SoundEvents.ARMOR_EQUIP_CHAIN)),
    PLATE("plate", ItemEffectTags.PLATE_ARMOR, List.of(SoundEvents.ARMOR_EQUIP_IRON, SoundEvents.ARMOR_EQUIP_GOLD, SoundEvents.ARMOR_EQUIP_COPPER)),
    CRYSTAL("crystal", ItemEffectTags.CRYSTAL_ARMOR, List.of(SoundEvents.ARMOR_EQUIP_DIAMOND)),
    HEAVY("heavy", ItemEffectTags.HEAVY_ARMOR, List.of(SoundEvents.ARMOR_EQUIP_NETHERITE));

    private static final Map<Identifier, ArmorFamily> BY_EQUIP_SOUND = new HashMap<>();

    static {
        for (var family : values())
            for (var sound : family.equipSounds)
                BY_EQUIP_SOUND.put(sound.value().location(), family);
    }

    // Looked up when a sound is first asked for, not when the class loads (as ItemClassType does)
    private static final class Sounds {
        static final ISoundLibrary LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    }

    private final String name;
    private final TagKey<Item> tag;
    private final List<Holder<SoundEvent>> equipSounds;
    private final Identifier stepSound;
    private final Identifier equipSound;

    ArmorFamily(String name, TagKey<Item> tag, List<Holder<SoundEvent>> equipSounds) {
        this.name = name;
        this.tag = tag;
        this.equipSounds = equipSounds;
        this.stepSound = Constants.asId("armor." + name + ".step");
        this.equipSound = Constants.asId("armor." + name + ".equip");
    }

    public String getName() {
        return this.name;
    }

    /**
     * The tag that puts a piece in this family whatever its equip sound says.
     */
    public TagKey<Item> tag() {
        return this.tag;
    }

    /**
     * The family whose pieces equip with this sound, or null for one no family claims.
     */
    public static @Nullable ArmorFamily byEquipSound(@Nullable SoundEvent sound) {
        return sound == null ? null : BY_EQUIP_SOUND.get(sound.location());
    }

    /**
     * Null when the factory is missing (a resource pack removed it), and the piece's own equip sound is used instead.
     */
    public @Nullable ISoundFactory getStepSound() {
        return Sounds.LIBRARY.getSoundFactory(this.stepSound).orElse(null);
    }

    /**
     * Null when the factory is missing, as {@link #getStepSound()}.
     */
    public @Nullable ISoundFactory getEquipSound() {
        return Sounds.LIBRARY.getSoundFactory(this.equipSound).orElse(null);
    }

    public Identifier stepSoundId() {
        return this.stepSound;
    }

    public Identifier equipSoundId() {
        return this.equipSound;
    }
}
