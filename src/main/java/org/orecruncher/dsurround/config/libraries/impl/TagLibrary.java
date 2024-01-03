package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
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
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class TagLibrary implements ITagLibrary {

    private final IPlatform platform;
    private final IModLog logger;

    private final Map<TagKey<?>, TagData<?>> tagCache = new Reference2ObjectOpenHashMap<>();

    public TagLibrary(IPlatform platform, IModLog logger) {
        this.platform = platform;
        this.logger = logger;
    }

    @Override
    public boolean is(TagKey<Block> tagKey, BlockState entry) {
        // For our purposes, blocks that are ignored will not have the
        // tags we are interested in.
        if (Constants.BLOCKS_TO_IGNORE.contains(entry.getBlock()))
            return false;
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, entry);
    }

    @Override
    public boolean is(TagKey<Item> tagKey, ItemStack entry) {
        if (entry.is(tagKey))
            return true;
        return this.isInCache(tagKey, entry);
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
        return this.tagCache.entrySet().stream()
                .map(kvp -> {
                    var builder = new StringBuilder();

                    builder.append("Tag: ").append(kvp.getKey().toString());
                    var td = kvp.getValue();

                    if (td.isEmpty()) {
                        // Makes it easier to spot in the logs
                        builder.append("\n*** EMPTY ***");
                    } else {
                        this.formatHelper(builder, "Members", td.completeIds());
                        this.formatHelper(builder, "Child Tags", td.immediateChildTags());
                        this.formatHelper(builder, "Direct", td.immediateChildIds());
                    }

                    builder.append("\n");
                    return builder.toString();
                })
                .sorted();
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
        this.logger.info("Clearing TagKey cache - total entries before clear: %d", this.tagCache.size());
        this.tagCache.clear();
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
        Set<TagKey<T>> tags = registryEntry.tags().collect(toSet());
        var entryId = this.getObjectIdentifier(registryEntry).orElseThrow();
        for (var kvp: this.tagCache.entrySet()) {
            var cachedTag = kvp.getKey();
            if (kvp.getValue().completeIds().contains(entryId))
                tags.add((TagKey<T>)cachedTag);
        }
        return tags.stream();
    }

    private boolean isInCache(TagKey<?> tagKey, Object entry) {
        return this.getTagData(tagKey).members().contains(entry);
    }

    private void formatHelper(StringBuilder builder, String entryName, Set<?> data) {
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

    private <T> Optional<ResourceLocation> getObjectIdentifier(Holder<T> registryEntry) {
        return registryEntry.unwrapKey().map(ResourceKey::location);
    }

    private <T> Optional<Registry<T>> getRegistry(TagKey<T> tagKey) {
        return RegistryUtils.getRegistry(tagKey.registry());
    }

    private <T> TagData<T> getTagData(TagKey<T> tagKey) {
        // We cannot use computeIfAbsent() because tag loading is a
        // recursive process and will refer back and potentially modify the
        // tag cache.
        var data = this.tagCache.get(tagKey);
        if (data == null) {
            data = this.loadTagData(tagKey);
            this.tagCache.put(tagKey, data);
        }
        return TagData.cast(data);
    }

    // This is based on the Fabric client tag handling code.
    private <T> TagData<T> loadTagData(TagKey<T> tagKey) {
        Set<TagEntry> entries = new HashSet<>();
        Set<Path> tagFiles = this.getTagFiles(tagKey.registry(), tagKey.location());

        for (Path tagPath : tagFiles) {
            try (BufferedReader tagReader = Files.newBufferedReader(tagPath)) {
                JsonElement jsonElement = JsonParser.parseReader(tagReader);
                TagFile maybeTagFile = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, jsonElement))
                        .result().orElse(null);

                if (maybeTagFile != null) {
                    if (maybeTagFile.replace()) {
                        entries.clear();
                    }

                    entries.addAll(maybeTagFile.entries());
                }
            } catch (IOException e) {
                this.logger.error(e, "Error loading tag: " + tagKey);
            }
        }

        if (entries.isEmpty()) {
            // This could be legitimately possible. However, if there is a typo in the json, like
            // c:glass_pane vs. c:glass_panes, it could result in an empty scan.  Ask me how I
            // know.
            return TagData.empty();
        }

        Set<ResourceLocation> completeIds = new HashSet<>();
        Set<ResourceLocation> immediateChildIds = new HashSet<>();
        Set<TagKey<?>> immediateChildTags = new HashSet<>();

        for (TagEntry tagEntry : entries) {
            tagEntry.build(new TagEntry.Lookup<>() {
                @Nullable
                @Override
                public ResourceLocation element(ResourceLocation id) {
                    immediateChildIds.add(id);
                    return id;
                }

                @Nullable
                @Override
                public Collection<ResourceLocation> tag(ResourceLocation id) {
                    TagKey<?> tag = TagKey.create(tagKey.registry(), id);
                    immediateChildTags.add(tag);
                    // This will trigger recursion to generate a complete list of IDs
                    return getTagData(tag).completeIds();
                }
            }, completeIds::add);
        }

        // Make sure the current tag is not in the immediate list
        immediateChildTags.remove(tagKey);

        // Manifest the completeId list into object instances for identity lookup
        var registry = this.getRegistry(tagKey).orElseThrow();
        var instances = new ArrayList<T>();
        for (var id : completeIds) {
            var rk = ResourceKey.create(tagKey.registry(), id);
            var instance = registry.get(rk);
            instances.add(instance);
        }

        return new TagData<>(
                new ReferenceOpenHashSet<>(instances),
                ImmutableSet.copyOf(completeIds),
                ImmutableSet.copyOf(immediateChildTags),
                ImmutableSet.copyOf(immediateChildIds));
    }

    private Set<Path> getTagFiles(ResourceKey<? extends Registry<?>> registryKey, ResourceLocation identifier) {
        var tagType = TagManager.getTagDir(registryKey);
        var tagFile = "data/%s/%s/%s.json".formatted(identifier.getNamespace(), tagType, identifier.getPath());
        return this.platform.getResourcePaths(tagFile);
    }

    private record TagData<T>(
            Set<T> members,
            Set<ResourceLocation> completeIds,
            Set<TagKey<?>> immediateChildTags,
            Set<ResourceLocation> immediateChildIds) {
        private static final TagData<?> EMPTY = new TagData<>(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());

        public static <T> TagData<T> empty() {
            return cast(EMPTY);
        }

        @SuppressWarnings("unchecked")
        public static <T> TagData<T> cast(TagData<?> tagData) {
            return (TagData<T>)tagData;
        }

        public boolean isEmpty() {
            return this.members.isEmpty() && this.immediateChildIds.isEmpty() && this.immediateChildTags.isEmpty();
        }
    }
}
