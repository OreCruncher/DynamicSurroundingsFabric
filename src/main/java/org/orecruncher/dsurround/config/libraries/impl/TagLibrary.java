package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ClientTagLoader;
import org.orecruncher.dsurround.tags.ModTags;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class TagLibrary implements ITagLibrary {

    private final IModLog logger;

    private final Map<TagKey<?>, Collection<?>> tagCache;
    private final ClientTagLoader tagLoader;

    public TagLibrary(IModLog logger) {
        this.logger = logger;
        this.tagCache = new Reference2ObjectOpenHashMap<>();
        this.tagLoader = new ClientTagLoader(tag -> ModTags.getModTags().contains(tag));
    }

    @Override
    public boolean is(TagKey<Block> tagKey, BlockState entry) {
        // For our purposes, blocks that are ignored will not have the
        // tags we are interested in.
        var block = entry.getBlock();
        if (Constants.BLOCKS_TO_IGNORE.contains(entry.getBlock()))
            return false;
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, block);
    }

    @Override
    public boolean is(TagKey<Item> tagKey, ItemStack entry) {
        if (entry.isEmpty())
            return false;
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, entry.getItem());
    }

    @Override
    public boolean is(TagKey<Biome> tagKey, Biome entry) {
        var registryEntry = RegistryUtils.getRegistryEntry(Registries.BIOME, entry);
        if (registryEntry.isPresent() && registryEntry.get().is(tagKey))
            return true;
        return this.isInCache(tagKey, entry);
    }

    @Override
    public boolean is(TagKey<EntityType<?>> tagKey, EntityType<?> entry) {
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, entry);
    }

    @Override
    public Stream<String> dump() {
        return Stream.of();
    }

    @Override
    public void reload() {
        this.logger.info("Clearing tag cache; %d elements were present", this.tagCache.size());
        this.tagCache.clear();
        this.tagLoader.clear();
    }

    @Override
    public <T> String asString(Stream<TagKey<T>> tagStream) {
        return tagStream
                .map(key -> key.location().toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    @Override
    public <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(ResourceKey<? extends Registry<T>> registryKey) {
        var registry = RegistryUtils.getRegistry(registryKey).orElseThrow();
        return registry.holders()
                .flatMap(e -> this.streamTags(e).map(tag -> Pair.of(tag, e.value())))
                .collect(groupingBy(Pair::key, mapping(Pair::value, toSet())))
                .entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Stream<TagKey<T>> streamTags(Holder<T> registryEntry) {
        T entry = registryEntry.value();
        Set<TagKey<T>> tags = registryEntry.tags().collect(toSet());
        for (var kvp : this.tagCache.entrySet()) {
            if (kvp.getValue().contains(entry))
                tags.add((TagKey<T>) kvp.getKey());
        }
        return tags.stream();
    }

    private boolean isInCache(TagKey<?> tagKey, Object entry) {
        return this.tagCache.computeIfAbsent(tagKey, this.tagLoader::getMembers).contains(entry);
    }
}
