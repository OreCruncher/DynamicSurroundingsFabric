package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.ItemEffectTags;
import org.orecruncher.dsurround.tags.ItemTags;
import org.orecruncher.dsurround.tags.TagHelpers;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ItemLibrary implements IItemLibrary {

    private final IModLog logger;
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemSwingFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemArmorStepFactories = new Reference2ObjectOpenHashMap<>();
    private int version;

    public ItemLibrary(IModLog logger) {
        this.logger = logger;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
        this.itemEquipFactories.clear();
        this.itemSwingFactories.clear();
        this.itemArmorStepFactories.clear();
        this.version++;
        this.logger.info("Item library configured; version is now %d", version);
    }

    @Override
    public Optional<ISoundFactory> getItemEquipSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(this.itemEquipFactories.computeIfAbsent(stack.getItem(), k -> resolve(stack, ItemClassType::getToolBarSound, ItemClassType.NONE::getToolBarSound)));
    }

    @Override
    public Optional<ISoundFactory> getItemSwingSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(this.itemSwingFactories.computeIfAbsent(stack.getItem(), k -> resolve(stack, ItemClassType::getSwingSound, () -> null)));
    }

    @Override
    public Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(this.itemArmorStepFactories.computeIfAbsent(stack.getItem(), k -> resolveEquipStepSound(stack)));
    }

    @Override
    public Stream<String> dump() {
        var blockRegistry = GameUtils.getRegistryManager().get(RegistryKeys.ITEM).getEntrySet();
        return blockRegistry.stream().map(kvp -> formatItemOutput(kvp.getKey().getValue(), kvp.getValue())).sorted();
    }

    private static @Nullable ISoundFactory resolveEquipStepSound(ItemStack stack) {
        var sound = getEquipableSoundEvent(stack);
        if (sound != null)
            return SoundFactoryBuilder
                    .create(sound)
                    .category(SoundCategory.PLAYERS).volume(0.07F).pitchRange(0.8F, 1F).build();
        return null;
    }

    private static @Nullable ISoundFactory resolve(ItemStack stack, Function<ItemClassType, ISoundFactory> resolveSound, Supplier<ISoundFactory> defaultSoundFactory) {

        var itemClassType = resolveClassType(stack);

        if (itemClassType == ItemClassType.NONE) {
            SoundEvent itemEquipSound = getSoundEvent(stack);
            if (itemEquipSound != null)
                return SoundFactoryBuilder
                        .create(itemEquipSound)
                        .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build();
            return defaultSoundFactory.get();
        }

        return resolveSound.apply(itemClassType);
    }

    @Nullable
    private static SoundEvent getEquipableSoundEvent(ItemStack stack) {
        var item = stack.getItem();
        SoundEvent itemEquipSound = null;

        if (item instanceof Equipment equipment)
            itemEquipSound = equipment.getEquipSound();
        else if (item instanceof ArmorItem armor)
            itemEquipSound = armor.getEquipSound();

        return itemEquipSound;
    }

    @Nullable
    private static SoundEvent getSoundEvent(ItemStack stack) {
        // Look for special Equipment and ArmorItem types since they may have built in equip sounds
        SoundEvent itemEquipSound = getEquipableSoundEvent(stack);
        if (itemEquipSound != null)
            return itemEquipSound;

        var item = stack.getItem();

        if (item instanceof ElytraItem elytraItem)
            itemEquipSound = elytraItem.getEquipSound();
        else if (TagHelpers.isIn(ItemTags.LAVA_BUCKETS, item))
            itemEquipSound = SoundEvents.ITEM_BUCKET_FILL_LAVA;
        else if (TagHelpers.isIn(ItemTags.WATER_BUCKETS, item))
            itemEquipSound = SoundEvents.ITEM_BUCKET_FILL;
        else if (TagHelpers.isIn(ItemTags.ENTITY_WATER_BUCKETS, item))
            itemEquipSound = SoundEvents.ITEM_BUCKET_FILL_FISH;
        else if (TagHelpers.isIn(ItemTags.MILK_BUCKETS, item))
            itemEquipSound = SoundEvents.ITEM_BUCKET_FILL;

        return itemEquipSound;
    }

    private static ItemClassType resolveClassType(ItemStack stack) {
        var item = stack.getItem();
        if (TagHelpers.isIn(ItemEffectTags.AXES, item))
            return ItemClassType.AXE;
        if (TagHelpers.isIn(ItemEffectTags.BOOKS, item))
            return ItemClassType.BOOK;
        if (TagHelpers.isIn(ItemEffectTags.BOWS, item))
            return ItemClassType.BOW;
        if (TagHelpers.isIn(ItemEffectTags.POTIONS, item))
            return ItemClassType.POTION;
        if (TagHelpers.isIn(ItemEffectTags.CROSSBOWS, item))
            return ItemClassType.CROSSBOW;
        if (TagHelpers.isIn(ItemEffectTags.SHIELDS, item))
            return ItemClassType.SHIELD;
        if (TagHelpers.isIn(ItemEffectTags.SWORDS, item))
            return ItemClassType.SWORD;
        if (TagHelpers.isIn(ItemEffectTags.TOOLS, item))
            return ItemClassType.TOOL;

        return ItemClassType.NONE;
    }

    private static String formatItemOutput(Identifier id, Item item) {
        var items = GameUtils.getWorld()
                .getRegistryManager().get(RegistryKeys.ITEM);

        var tags = items.getEntry(items.getRawId(item))
                .map(e -> TagHelpers.asString(e.streamTags()))
                .orElse("null");

        return id.toString() + "\nTags: " + tags + "\n";
    }
}
