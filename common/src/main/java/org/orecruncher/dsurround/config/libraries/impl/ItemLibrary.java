package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.*;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
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
    // Resolved sounds per item. Optional.empty() records "this item has no sound", so it is cached like any other
    // answer: computeIfAbsent stores nothing for a null result, which would mean resolving those items on every use.
    //
    // PORTING: these caches are keyed by Item, which assumes an item's sounds don't depend on the individual stack.
    // That holds in 1.21.1, but see getEquipableSoundEvent(): once equippability is a per-stack component, two stacks
    // of the same item can have different equip sounds.
    private final Reference2ObjectOpenHashMap<Item, Optional<ISoundFactory>> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, Optional<ISoundFactory>> itemSwingFactories = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Item, Optional<ISoundFactory>> itemArmorStepFactories = new Reference2ObjectOpenHashMap<>();
    private int version;

    public ItemLibrary(ITagLibrary tagLibrary, Configuration config, IModLog logger) {
        this.tagLibrary = tagLibrary;
        this.logger = ModLog.createChild(logger, "ItemLibrary");
        this.config = config;

        // What an item resolves to depends on config (enableToolbarBlockSounds), so cached answers are dropped when
        // it changes
        ConfigurationData.CONFIG_CHANGED_EVENT.register(cfg -> this.clearCaches());
    }

    /**
     * Clears on every reload. Item sounds depend on tags (a tag sync) and on the sound library's factories, which a
     * resource reload rebuilds, so every scope can change the answers.
     */
    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        this.version++;
        this.clearCaches();
        this.logger.info("[ItemLibrary] Configured; version is now %d", this.version);
    }

    private void clearCaches() {
        this.itemEquipFactories.clear();
        this.itemSwingFactories.clear();
        this.itemArmorStepFactories.clear();
    }

    @Override
    public Optional<ISoundFactory> getItemEquipSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return this.itemEquipFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(resolve(stack, ItemClassType::getToolBarSound, ItemClassType.NONE::getToolBarSound)));
    }

    @Override
    public Optional<ISoundFactory> getItemSwingSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return this.itemSwingFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(resolve(stack, ItemClassType::getSwingSound, () -> null)));
    }

    @Override
    public Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return this.itemArmorStepFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(resolveEquipableStepSound(stack)));
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

    /**
     * PORTING: in 1.21.1 equippability comes from the Item (the Equipable interface), so caching by Item is safe.
     * In later versions (26.2 at least) the Equipable class is gone and equippability is the per-stack
     * DataComponents.EQUIPPABLE component (an Equippable record with equipSound), so this method has to be rewritten,
     * and the Item-keyed caches above stop being correct for stacks given a custom EQUIPPABLE (via /give, datapacks or
     * other mods).
     * <p>
     * Suggested approach: keep the Item-keyed caches for stacks whose EQUIPPABLE equals their item's default (nearly
     * all of them). Resolve stacks with a custom one without caching, or cache them in a second map keyed by the
     * Equippable record, which compares by value. Only EQUIPPABLE matters here; damage, enchantments and other
     * components don't affect these sounds, so "the stack has any component changes" is not the right test.
     */
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
        if (this.tagLibrary.is(ItemEffectTags.MACES, stack))
            return ItemClassType.MACE;
        if (this.tagLibrary.is(ItemEffectTags.POTIONS, stack))
            return ItemClassType.POTION;
        if (this.tagLibrary.is(ItemEffectTags.CROSSBOWS, stack))
            return ItemClassType.CROSSBOW;
        if (this.tagLibrary.is(ItemEffectTags.SHIELDS, stack))
            return ItemClassType.SHIELD;
        if (this.tagLibrary.is(ItemEffectTags.SPEARS, stack))
            return ItemClassType.SPEAR;
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
