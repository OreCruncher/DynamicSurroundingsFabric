// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.IClientConnect;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.ModLog;
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

/**
 * Tag membership checks. Vanilla and server tags are answered by the game's registries. The mod's own tags
 * ({@link ModTags}) exist only on the client: their members are loaded from tag files by {@link ClientTagLoader}
 * and cached here.
 * <p>
 * For the mod's tags on blocks, items, fluids, entity types and particle types the cache holds the member objects themselves, so a
 * check is one map lookup and one identity-set lookup. These checks run for every block the area scanner reads
 * (heat sources, steam producers, occlusion), so they need to be cheap. Biomes come from a per-world registry, so
 * their tags stay keyed by id.
 * <p>
 * Client thread only.
 */
public class TagLibrary implements ITagLibrary {

    // Registries whose mod tags are cached as sets of the member objects. These registries are fixed for the
    // session, so the objects stay valid.
    private static final Set<ResourceKey<? extends Registry<?>>> OBJECT_REGISTRIES = Set.of(
            Registries.BLOCK, Registries.ITEM, Registries.FLUID, Registries.ENTITY_TYPE, Registries.PARTICLE_TYPE);

    private final IModLog logger;
    private final ISystemClock systemClock;
    private final ClientTagLoader tagLoader;

    /**
     * The mod's tags and their members' ids (every tag in ModTags has an entry), and the same tags' members as objects,
     * for the registries in OBJECT_REGISTRIES, keyed by identity (TagKeys are interned). Never changed once built: a
     * new one replaces it, so a thread reading it sees one whole (rain surfaces are sorted on Async Particles' weather
     * thread, while a reload may run on the client's).
     */
    private record Cache(Map<TagKey<?>, Collection<Identifier>> ids, Map<TagKey<?>, Set<Object>> objects) {
    }

    // Null until the cache is built, and again after anything that makes it stale; the next check rebuilds it. Built
    // and dropped under the library's lock
    private volatile @Nullable Cache cache;

    private boolean isConnected;
    private volatile int version;

    public TagLibrary(IModLog logger, ISystemClock systemClock) {
        this.logger = ModLog.createChild(logger, "TagLibrary");
        this.systemClock = systemClock;
        this.tagLoader = new ClientTagLoader(ResourceUtilities.createForCurrentState(), logger, this.systemClock);

        // The cache is rebuilt for each connection, and dropped on disconnect: tag membership can differ between
        // servers.
        IClientConnect.EVENT.register(this::onConnect);
        IClientDisconnect.EVENT.register(this::onDisconnect);
    }

    @Override
    public boolean is(TagKey<Block> tagKey, BlockState entry) {
        // For our purposes, blocks that are ignored will not have the
        // tags we are interested in.
        var block = entry.getBlock();
        if (Constants.BLOCKS_TO_IGNORE.contains(block))
            return false;
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(block) : entry.is(tagKey);
    }

    @Override
    public boolean is(TagKey<Item> tagKey, ItemStack entry) {
        if (entry.isEmpty())
            return false;
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(entry.getItem()) : entry.is(tagKey);
    }

    /**
     * Checks the item directly, rather than going through a new ItemStack as the interface's default does.
     */
    @Override
    public boolean is(TagKey<Item> tagKey, Item item) {
        if (item == Items.AIR)
            return false;
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(item) : BuiltInRegistries.ITEM.wrapAsHolder(item).is(tagKey);
    }

    @Override
    public boolean is(TagKey<Biome> tagKey, Biome entry) {
        var registryEntry = RegistryUtils.getRegistryEntry(Registries.BIOME, entry);
        if (registryEntry.isPresent()) {
            var e = registryEntry.get();
            if (e.is(tagKey))
                return true;
            var ids = this.memberIds(tagKey);
            return ids != null && ids.contains(e.key().identifier());
        }
        return false;
    }

    @Override
    public boolean is(TagKey<EntityType<?>> tagKey, EntityType<?> entry) {
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(entry) : entry.arch$holder().is(tagKey);
    }

    @Override
    public boolean is(TagKey<Fluid> tagKey, FluidState entry) {
        if (entry.isEmpty())
            return false;
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(entry.getType()) : entry.is(tagKey);
    }

    @Override
    public boolean is(TagKey<ParticleType<?>> tagKey, ParticleType<?> entry) {
        var members = this.memberObjects(tagKey);
        return members != null ? members.contains(entry) : BuiltInRegistries.PARTICLE_TYPE.wrapAsHolder(entry).is(tagKey);
    }

    /**
     * The mod's own tags and their members. Tags from the game's registries aren't cached, so aren't listed.
     */
    @Override
    public Stream<String> dump() {
        return this.cache().ids().entrySet().stream()
                .map(kvp -> {
                    var builder = new StringBuilder();

                    builder.append("Tag: ").append(kvp.getKey().toString());
                    var members = kvp.getValue();

                    if (members.isEmpty()) {
                        // Makes it easier to spot in the logs
                        builder.append("\n*** EMPTY ***");
                    } else {
                        this.formatHelper(builder, "Members", members);
                    }

                    builder.append("\n");
                    return builder.toString();
                })
                .sorted();
    }

    @Override
    public synchronized void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        this.tagLoader.setResourceUtilities(resourceUtilities);

        var cache = this.cache;
        this.logger.info("Cache has %d elements", cache == null ? 0 : cache.ids().size());

        // Rebuilt whether connected or not, so the cache always matches the current resources. While connected it
        // is rebuilt now (a /dsreload, resource pack change, tag sync, ...); otherwise on next use.
        this.invalidate();
        if (this.isConnected)
            this.cache = this.initializeTagCache();
    }

    @Override
    public int getVersion() {
        return this.version;
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
        return registry.listElements()
                .flatMap(e -> this.streamTags(e).map(tag -> Pair.of(tag, e.value())))
                .collect(groupingBy(Pair::key, mapping(Pair::value, toSet())))
                .entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Stream<TagKey<T>> streamTags(Holder<T> registryEntry) {
        var cache = this.cache();
        var location = registryEntry.unwrapKey().orElseThrow().identifier();
        Set<TagKey<T>> tags = registryEntry.tags().collect(toSet());
        for (var kvp : cache.ids().entrySet()) {
            if (kvp.getValue().contains(location))
                tags.add((TagKey<T>) kvp.getKey());
        }
        return tags.stream();
    }

    private synchronized void onConnect(Minecraft client) {
        this.isConnected = true;
        this.tagLoader.setServerType(GameUtils.getServerType());
        this.invalidate();
        this.cache = this.initializeTagCache();
    }

    private synchronized void onDisconnect(Minecraft client) {
        this.isConnected = false;
        // Drop the last server's data; anything checked before the next connection gets a fresh build
        this.invalidate();
    }

    // ---- Cache -----------------------------------------------------------------------------------------------

    // Under the lock
    private void invalidate() {
        this.version++;
        this.cache = null;
    }

    /**
     * The cache, built first if it isn't (by one thread: another waits for it)
     */
    private Cache cache() {
        var cache = this.cache;
        return cache != null ? cache : this.build();
    }

    private synchronized Cache build() {
        var cache = this.cache;
        if (cache == null)
            this.cache = cache = this.initializeTagCache();
        return cache;
    }

    /**
     * Loads every mod tag's members, as ids and, where possible, as objects. Under the lock.
     */
    private Cache initializeTagCache() {
        var stopwatch = this.systemClock.getStopwatch();
        this.logger.info("Repopulating tag cache");
        this.tagLoader.clear();
        Map<TagKey<?>, Collection<Identifier>> ids = new Reference2ObjectOpenHashMap<>();
        Map<TagKey<?>, Set<Object>> objects = new Reference2ObjectOpenHashMap<>();
        for (var tagKey : ModTags.getModTags()) {
            var members = this.tagLoader.getMembers(tagKey);
            ids.put(tagKey, members);
            this.buildMemberObjects(tagKey, members, objects);
        }
        this.logger.info("Tag cache initialization complete; %d tags cached, %dmillis", ids.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return new Cache(ids, objects);
    }

    /**
     * Resolves a mod tag's member ids to the objects in its registry, if that registry is one of OBJECT_REGISTRIES.
     * Members the registries themselves give the tag are included too (only possible if a datapack defines one of
     * the mod's tags), so a check against the set alone matches checking both, as the id-based cache used to.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void buildMemberObjects(TagKey<?> tagKey, Collection<Identifier> ids, Map<TagKey<?>, Set<Object>> objects) {
        if (!OBJECT_REGISTRIES.contains(tagKey.registry()))
            return;
        var found = RegistryUtils.getRegistry((ResourceKey) tagKey.registry());
        if (found.isEmpty())
            return;
        Registry<Object> registry = (Registry<Object>) found.get();

        Set<Object> members = new ReferenceOpenHashSet<>(ids.size());
        for (var id : ids)
            registry.getOptional(id).ifPresent(members::add);
        registry.getTagOrEmpty((TagKey<Object>) tagKey).forEach(h -> members.add(h.value()));
        objects.put(tagKey, members);
    }

    /**
     * The member objects if {@code tagKey} is one of the mod's tags on an object registry, otherwise null (the
     * game's registries answer for other tags).
     */
    private @Nullable Set<Object> memberObjects(TagKey<?> tagKey) {
        return this.cache().objects().get(tagKey);
    }

    /**
     * The member ids if {@code tagKey} is one of the mod's tags, otherwise null.
     */
    private @Nullable Collection<Identifier> memberIds(TagKey<?> tagKey) {
        return this.cache().ids().get(tagKey);
    }

    private void formatHelper(StringBuilder builder, String entryName, Collection<Identifier> data) {
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
