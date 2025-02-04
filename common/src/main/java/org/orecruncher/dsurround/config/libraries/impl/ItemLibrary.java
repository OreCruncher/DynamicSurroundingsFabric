package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.*;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.ItemEffectTags;
import org.orecruncher.dsurround.tags.ItemTags;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ItemLibrary implements IItemLibrary {

    private final ITagLibrary tagLibrary;
    private final IModLog logger;
    private final Configuration config;
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemSwingFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemArmorStepFactories = new Reference2ObjectOpenHashMap<>();
    private int version;

    public ItemLibrary(ITagLibrary tagLibrary, Configuration config, IModLog logger) {
        this.tagLibrary = tagLibrary;
        this.logger = ModLog.createChild(logger, "ItemLibrary");
        this.config = config;
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (scope != IReloadEvent.Scope.RESOURCES) {
            this.version++;
            this.itemEquipFactories.clear();
            this.itemSwingFactories.clear();
            this.itemArmorStepFactories.clear();
            this.logger.info("[ItemLibrary] Configured; version is now %d", this.version);
        }
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
        return Optional.ofNullable(this.itemArmorStepFactories.computeIfAbsent(stack.getItem(), k -> resolveEquipableStepSound(stack)));
    }

    @Override
    public Stream<String> dump() {
        var itemRegistry = RegistryUtils.getRegistry(Registries.ITEM).map(Registry::entrySet).orElseThrow();
        return itemRegistry.stream().map(kvp -> formatItemOutput(kvp.getKey().location(), kvp.getValue())).sorted();
    }

    private static @Nullable ISoundFactory resolveEquipableStepSound(ItemStack stack) {
        var sound = getEquipableSoundEvent(stack);
        if (sound != null)
            return SoundFactoryBuilder
                    .create(sound)
                    .category(SoundSource.PLAYERS).volume(0.07F).pitch(0.8F, 1F).build();
        return null;
    }

    private @Nullable ISoundFactory resolve(ItemStack stack, Function<ItemClassType, ISoundFactory> resolveSound, Supplier<ISoundFactory> defaultSoundFactory) {

        var itemClassType = resolveClassType(stack);

        if (itemClassType == ItemClassType.NONE) {
            SoundEvent itemEquipSound = getSoundEvent(stack);
            if (itemEquipSound != null)
                return SoundFactoryBuilder
                        .create(itemEquipSound)
                        .category(SoundSource.PLAYERS).volume(0.25F).pitch(0.8F, 1.2F).build();
            return defaultSoundFactory.get();
        }

        return resolveSound.apply(itemClassType);
    }

    @Nullable
    private static SoundEvent getEquipableSoundEvent(ItemStack stack) {
        SoundEvent itemEquipSound = null;
        var equipable = Equipable.get(stack);
        if (equipable != null) {
            itemEquipSound = equipable.getEquipSound().value();
        }
        return itemEquipSound;
    }

    @Nullable
    private SoundEvent getSoundEvent(ItemStack stack) {
        // Look for special Equipment and ArmorItem types since they may have built in equipped sounds
        SoundEvent itemEquipSound = getEquipableSoundEvent(stack);
        if (itemEquipSound != null)
            return itemEquipSound;

        if (this.config.entityEffects.enableToolbarBlockSounds) {
            Item item = stack.getItem();
            if (item instanceof BlockItem blockItem) {
                var soundType = blockItem.getBlock().defaultBlockState().getSoundType();
                itemEquipSound = soundType.getStepSound();
            }
        }

        if (itemEquipSound != null)
            return itemEquipSound;

        if (this.tagLibrary.is(ItemTags.LAVA_BUCKETS, stack))
            itemEquipSound = SoundEvents.BUCKET_FILL_LAVA;
        else if (this.tagLibrary.is(ItemTags.WATER_BUCKETS, stack))
            itemEquipSound = SoundEvents.BUCKET_FILL;
        else if (this.tagLibrary.is(ItemTags.ENTITY_WATER_BUCKETS, stack))
            itemEquipSound = SoundEvents.BUCKET_FILL_FISH;
        else if (this.tagLibrary.is(ItemTags.MILK_BUCKETS, stack))
            itemEquipSound = SoundEvents.BUCKET_FILL;

        return itemEquipSound;
    }

    private ItemClassType resolveClassType(ItemStack stack) {
        if (this.tagLibrary.is(ItemEffectTags.AXES, stack))
            return ItemClassType.AXE;
        if (this.tagLibrary.is(ItemEffectTags.BOOKS, stack))
            return ItemClassType.BOOK;
        if (this.tagLibrary.is(ItemEffectTags.BOWS, stack))
            return ItemClassType.BOW;
        if (this.tagLibrary.is(ItemEffectTags.POTIONS, stack))
            return ItemClassType.POTION;
        if (this.tagLibrary.is(ItemEffectTags.CROSSBOWS, stack))
            return ItemClassType.CROSSBOW;
        if (this.tagLibrary.is(ItemEffectTags.SHIELDS, stack))
            return ItemClassType.SHIELD;
        if (this.tagLibrary.is(ItemEffectTags.SWORDS, stack))
            return ItemClassType.SWORD;
        if (this.tagLibrary.is(ItemEffectTags.TOOLS, stack))
            return ItemClassType.TOOL;

        return ItemClassType.NONE;
    }

    private String formatItemOutput(ResourceLocation id, Item item) {
        var tags = RegistryUtils.getRegistryEntry(Registries.ITEM, item)
                .map(e -> {
                    var t = this.tagLibrary.streamTags(e);
                    return this.tagLibrary.asString(t);
                })
                .orElse("null");

        return id.toString() + "\nTags: " + tags + "\n";
    }
}
