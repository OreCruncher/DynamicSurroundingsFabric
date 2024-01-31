package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ClientTagLoader;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.tags.ModTags;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class TagLibrary implements ITagLibrary {

    private final IModLog logger;
    private final ISystemClock systemClock;

    private final Map<TagKey<?>, Collection<?>> tagCache;
    private ClientTagLoader tagLoader;

    private boolean isConnected;

    public TagLibrary(IModLog logger, ISystemClock systemClock) {
        this.logger = logger;
        this.systemClock = systemClock;
        this.tagCache = new Reference2ObjectOpenHashMap<>();

        // Need to clear the tag caches on disconnect. It's possible that
        // cached biome information will change with the next connection.
        ClientState.ON_CONNECT.register(this::onConnect);
        ClientState.ON_DISCONNECT.register(this::onDisconnect);
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
    public boolean is(TagKey<Fluid> tagKey, FluidState entry) {
        if (entry.isEmpty())
            return false;
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, entry.getType());
    }

    @Override
    public Stream<String> dump() {
        return this.tagCache.entrySet().stream()
                .map(kvp -> {
                    var builder = new StringBuilder();

                    builder.append("Tag: ").append(kvp.getKey().toString());
                    var td = kvp.getValue();

                    if (td.isEmpty()) {
                        // Makes it easier to spot in the logs
                        builder.append("\n*** EMPTY ***");
                    } else {
                        this.formatHelper(builder, "Members", this.tagLoader.getCompleteIds(kvp.getKey()));
                    }

                    builder.append("\n");
                    return builder.toString();
                })
                .sorted();
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (resourceUtilities != null)
            this.tagLoader = new ClientTagLoader(resourceUtilities, this.logger, this.systemClock);

        if (scope == IReloadEvent.Scope.RESOURCES)
            return;

        this.logger.info("[TagLibrary] Cache has %d elements", this.tagCache.size());

        // If we are connected to a server, and we get a reload something triggered it
        // like a /dsreload, resource pack change, etc.
        if (this.isConnected)
            this.initializeTagCache();
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

    private void onConnect(Minecraft client) {
        this.isConnected = true;
        this.initializeTagCache();
    }

    private void onDisconnect(Minecraft client) {
        this.isConnected = false;
    }

    private void initializeTagCache() {
        if (this.tagLoader == null)
            return;

        // Bootstrap the tag cache. We do this by zipping through our tags
        // and forcing the cache to initialize.
        var stopwatch = this.systemClock.getStopwatch();
        this.logger.info("Repopulating tag cache");
        this.tagCache.clear();
        this.tagLoader.clear();
        for (var tagKey : ModTags.getModTags())
            this.tagCache.computeIfAbsent(tagKey, this.tagLoader::getMembers);
        this.logger.info("Tag cache initialization complete; %d tags cached, %dmillis", this.tagCache.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    private boolean isInCache(TagKey<?> tagKey, Object entry) {
        if (!ModTags.getModTags().contains(tagKey))
            return false;
        return this.tagCache.computeIfAbsent(tagKey, this.tagLoader::getMembers).contains(entry);
    }

    private void formatHelper(StringBuilder builder, String entryName, Collection<ResourceLocation> data) {
        builder.append("\n").append(entryName).append(" ");
        if (data.isEmpty())
            builder.append("NONE");
        else {
            builder.append("[");
            for (var e : data)
                builder.append("\n  ").append(e.toString());
            builder.append("\n]");
        }
    }
}
