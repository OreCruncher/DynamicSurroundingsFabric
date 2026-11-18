// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.ArmorFamily;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.config.libraries.impl.ItemBehavior.Classification;
import org.orecruncher.dsurround.eventing.IConfigChangedEvent;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.ItemEffectTags;
import org.orecruncher.dsurround.tags.ItemTags;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ItemLibrary implements IItemLibrary {

    // Our class tags, in the order they are tried: the first an item is in says what it is
    private static final List<Map.Entry<TagKey<Item>, ItemClassType>> CLASS_TAGS = List.of(
            Map.entry(ItemEffectTags.AXES, ItemClassType.AXE),
            Map.entry(ItemEffectTags.PICKAXES, ItemClassType.PICKAXE),
            Map.entry(ItemEffectTags.BOOKS, ItemClassType.BOOK),
            Map.entry(ItemEffectTags.BOWS, ItemClassType.BOW),
            Map.entry(ItemEffectTags.MACES, ItemClassType.MACE),
            Map.entry(ItemEffectTags.POTIONS, ItemClassType.POTION),
            Map.entry(ItemEffectTags.CROSSBOWS, ItemClassType.CROSSBOW),
            Map.entry(ItemEffectTags.SHIELDS, ItemClassType.SHIELD),
            Map.entry(ItemEffectTags.SPEARS, ItemClassType.SPEAR),
            Map.entry(ItemEffectTags.SWORDS, ItemClassType.SWORD),
            Map.entry(ItemEffectTags.TOOLS, ItemClassType.TOOL));

    // The convention tags for mining tools in general (singular on NeoForge, both on Fabric): the last thing looked at
    private static final TagKey<Item> MINING_TOOL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/mining_tool"));
    private static final TagKey<Item> MINING_TOOLS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/mining_tools"));

    /**
     * A piece's armor family, or null for none, and why.
     */
    record FamilyResult(@Nullable ArmorFamily family, String reason) {
    }

    /**
     * A sound an item makes, or null for none, and where it came from.
     */
    record SoundResult(@Nullable ISoundFactory factory, String source) {
    }

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
        IConfigChangedEvent.EVENT.register(cfg -> this.clearCaches());
    }

    /**
     * Clears on every reload. Item sounds depend on tags (a tag sync) and on the sound library's factories, which a
     * resource reload rebuilds, so every scope can change the answers.
     */
    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        this.version++;
        this.clearCaches();
        this.logger.info("Configured; version is now %d", this.version);
    }

    @Override
    public int getVersion() {
        return this.version;
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
        return this.itemEquipFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(explainEquipSound(stack).factory()));
    }

    @Override
    public Optional<ISoundFactory> getItemSwingSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return this.itemSwingFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(explainSwingSound(stack).factory()));
    }

    @Override
    public Optional<ISoundFactory> getEquipableStepAccentSound(ItemStack stack) {
        if (stack.isEmpty())
            return Optional.empty();
        return this.itemArmorStepFactories.computeIfAbsent(stack.getItem(), k -> Optional.ofNullable(explainStepSound(stack).factory()));
    }

    @Override
    public Stream<String> dump() {
        return this.dump(null);
    }

    /**
     * A report for checking a pack: a summary (counts by class and armor family, and the items that need a look),
     * then each item with its tags, class, armor family and sounds, each with why. Only one mod's items for a
     * namespace; every item for null. Each item is resolved from its default stack, as the caches are.
     */
    @Override
    public Stream<String> dump(@Nullable String namespace) {
        var itemRegistry = RegistryUtils.getRegistry(Registries.ITEM).map(Registry::entrySet).orElseThrow();
        var reports = itemRegistry.stream()
                .filter(kvp -> namespace == null || kvp.getKey().identifier().getNamespace().equals(namespace))
                .map(kvp -> this.report(kvp.getKey().identifier(), kvp.getValue()))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        return ItemAudit.format(namespace, reports);
    }

    private ItemAudit.Report report(Identifier id, Item item) {
        var stack = new ItemStack(item);
        var holder = RegistryUtils.getRegistryEntry(Registries.ITEM, item);
        var tags = holder.map(e -> this.tagLibrary.asString(this.tagLibrary.streamTags(e))).orElse("null");
        var toolTags = holder.map(e -> this.tagLibrary.streamTags(e)
                        .map(TagKey::location)
                        .filter(t -> t.getNamespace().equals("c") && t.getPath().startsWith("tools/"))
                        .toList())
                .orElse(List.of());

        if (stack.isEmpty())
            return new ItemAudit.Report(id, tags, Classification.NONE, null, Classification.NONE,
                    new FamilyResult(null, "not worn"), false, toolTags,
                    new SoundResult(null, "an empty stack"), new SoundResult(null, "an empty stack"), new SoundResult(null, "an empty stack"));

        var equippable = stack.get(DataComponents.EQUIPPABLE);
        boolean wornArmor = equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        return new ItemAudit.Report(id, tags, this.explainClassType(stack), this.classByTag(stack), ItemBehavior.explain(stack),
                this.explainArmorFamily(stack), wornArmor, toolTags,
                this.explainEquipSound(stack), this.explainSwingSound(stack), this.explainStepSound(stack));
    }

    private SoundResult explainEquipSound(ItemStack stack) {
        var type = this.explainClassType(stack).type();
        if (type != ItemClassType.NONE)
            return new SoundResult(type.getToolBarSound(), "class " + type.getName());

        // Armor only sounds as its family where nothing else says what the item is
        var family = this.explainArmorFamily(stack).family();
        if (family != null) {
            var factory = family.getEquipSound();
            if (factory != null)
                return new SoundResult(factory, "armor family " + family.getName());
        }

        var own = this.ownSound(stack);
        if (own != null)
            return own;
        return new SoundResult(ItemClassType.NONE.getToolBarSound(), "the default");
    }

    private SoundResult explainSwingSound(ItemStack stack) {
        var type = this.explainClassType(stack).type();
        if (type != ItemClassType.NONE) {
            var factory = type.getSwingSound();
            return new SoundResult(factory, factory == null ? "class " + type.getName() + " has none" : "class " + type.getName());
        }

        // Armor swings with its family's equip sound, as it sounds in the hotbar
        var family = this.explainArmorFamily(stack).family();
        if (family != null) {
            var factory = family.getEquipSound();
            if (factory != null)
                return new SoundResult(factory, "armor family " + family.getName());
        }

        var own = this.ownSound(stack);
        if (own != null)
            return own;
        return new SoundResult(null, "no class");
    }

    private SoundResult explainStepSound(ItemStack stack) {
        var family = this.explainArmorFamily(stack).family();
        if (family != null) {
            var factory = family.getStepSound();
            if (factory != null)
                return new SoundResult(factory, "armor family " + family.getName());
        }

        // In no family: the piece's own equip sound, played quietly
        var sound = getEquipableSoundEvent(stack);
        if (sound != null)
            return new SoundResult(SoundFactoryBuilder
                    .create(sound)
                    .category(SoundSource.PLAYERS).volume(0.07F).pitch(0.8F, 1F).build(), "its own equip sound, quietly");
        return new SoundResult(null, "not worn");
    }

    /**
     * For an item of no class: its own equip sound, or a bucket's fill sound. Null for neither.
     */
    private @Nullable SoundResult ownSound(ItemStack stack) {
        var equipSound = getEquipableSoundEvent(stack);
        var sound = equipSound != null ? equipSound : this.bucketSound(stack);
        if (sound == null)
            return null;
        return new SoundResult(SoundFactoryBuilder
                .create(sound)
                .category(SoundSource.PLAYERS).volume(0.25F).pitch(0.8F, 1.2F).build(),
                equipSound != null ? "its own equip sound" : "a bucket's sound");
    }

    /**
     * The piece's family: a family's tag, or else its equip sound.
     */
    private FamilyResult explainArmorFamily(ItemStack stack) {
        for (var family : ArmorFamily.values())
            if (this.tagLibrary.is(family.tag(), stack))
                return new FamilyResult(family, "tag #" + family.tag().location());
        var sound = getEquipableSoundEvent(stack);
        if (sound == null)
            return new FamilyResult(null, "not worn");
        var family = ArmorFamily.byEquipSound(sound);
        if (family == null)
            return new FamilyResult(null, "equip sound " + sound.location() + " is in no family");
        return new FamilyResult(family, "equip sound " + sound.location());
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
        var equipable = stack.getComponents().get(DataComponents.EQUIPPABLE);
        return equipable == null ? null : equipable.equipSound().value();
    }

    @Nullable
    private SoundEvent bucketSound(ItemStack stack) {
        if (this.tagLibrary.is(ItemTags.LAVA_BUCKETS, stack))
            return SoundEvents.BUCKET_FILL_LAVA;
        if (this.tagLibrary.is(ItemTags.WATER_BUCKETS, stack))
            return SoundEvents.BUCKET_FILL;
        if (this.tagLibrary.is(ItemTags.ENTITY_WATER_BUCKETS, stack))
            return SoundEvents.BUCKET_FILL_FISH;
        if (this.tagLibrary.is(ItemTags.MILK_BUCKETS, stack))
            return SoundEvents.BUCKET_FILL;
        return null;
    }

    /**
     * The class our tags give the item, or null for none.
     */
    private @Nullable Classification classByTag(ItemStack stack) {
        for (var entry : CLASS_TAGS)
            if (this.tagLibrary.is(entry.getKey(), stack))
                return new Classification(entry.getValue(), "tag #" + entry.getKey().location());
        return null;
    }

    private Classification explainClassType(ItemStack stack) {
        var byTag = this.classByTag(stack);
        if (byTag != null)
            return byTag;

        // No tag of ours: what the item does, then a mining tool the convention tags know of but whose tool says
        // nothing more
        var byBehavior = ItemBehavior.explain(stack);
        if (byBehavior.type() != ItemClassType.NONE)
            return new Classification(byBehavior.type(), "behavior: " + byBehavior.reason());
        if (this.tagLibrary.is(MINING_TOOL, stack))
            return new Classification(ItemClassType.TOOL, "convention tag #" + MINING_TOOL.location());
        if (this.tagLibrary.is(MINING_TOOLS, stack))
            return new Classification(ItemClassType.TOOL, "convention tag #" + MINING_TOOLS.location());

        return new Classification(ItemClassType.NONE, "no tag of ours, and " + Classification.NONE.reason());
    }
}
