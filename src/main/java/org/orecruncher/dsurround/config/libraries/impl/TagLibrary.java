package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import org.jetbrains.annotations.Nullable;
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

    private final Map<TagKey<?>, TagData> tagCache = new IdentityHashMap<>();

    public TagLibrary(IPlatform platform, IModLog logger) {
        this.platform = platform;
        this.logger = logger;
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
                        this.formatHelper(builder, "Members", td.members());
                        this.formatHelper(builder, "Tags", td.immediateChildTags());
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
    public <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return this.getRegistryEntry(tagKey, entry)
                .map(re -> this.isIn(tagKey, re))
                .orElse(false);
    }

    @Override
    public <T> boolean isIn(TagKey<T> tagKey, Holder<T> registryEntry) {
        // Check dynamic registry first - may have set by the server
        if (registryEntry.is(tagKey)) {
            return true;
        }

        // Fallback to data packs on the client
        return this.getObjectIdentifier(registryEntry)
                .map(id -> this.getTagData(tagKey).members().contains(id))
                .orElse(false);
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
            if (kvp.getValue().members().contains(entryId))
                tags.add((TagKey<T>)cachedTag);
        }
        return tags.stream();
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

    private <T> Optional<Holder<T>> getRegistryEntry(TagKey<T> tagKey, T entry) {
        var maybeRegistry = getRegistry(tagKey);

        if (maybeRegistry.isEmpty() || !tagKey.isFor(maybeRegistry.get().key())) {
            return Optional.empty();
        }

        Registry<T> registry = maybeRegistry.get();

        Optional<ResourceKey<T>> maybeKey = registry.getResourceKey(entry);

        return maybeKey.map(registry::getHolderOrThrow);
    }

    private <T> Optional<Registry<T>> getRegistry(TagKey<T> tagKey) {
        return RegistryUtils.getRegistry(tagKey.registry());
    }

    private TagData getTagData(TagKey<?> tagKey) {
        // We cannot use computeIfAbsent() because tag loading is a
        // recursive process and will refer back and potentially modify the
        // tag cache.
        var data = this.tagCache.get(tagKey);
        if (data == null) {
            data = this.loadTagData(tagKey);
            this.tagCache.put(tagKey, data);
        }
        return data;
    }

    // This is based on the Fabric client tag handling code.
    private TagData loadTagData(TagKey<?> tagKey) {
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
            return TagData.EMPTY;
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
                    return getTagData(tag).members();
                }
            }, completeIds::add);
        }

        immediateChildTags.remove(tagKey);
        return new TagData(ImmutableSet.copyOf(completeIds), ImmutableSet.copyOf(immediateChildTags), ImmutableSet.copyOf(immediateChildIds));
    }

    private Set<Path> getTagFiles(ResourceKey<? extends Registry<?>> registryKey, ResourceLocation identifier) {
        var tagType = TagManager.getTagDir(registryKey);
        var tagFile = "data/%s/%s/%s.json".formatted(identifier.getNamespace(), tagType, identifier.getPath());
        return this.platform.getResourcePaths(tagFile);
    }

    private record TagData(Set<ResourceLocation> members, Set<TagKey<?>> immediateChildTags,
                           Set<ResourceLocation> immediateChildIds) {
        public static final TagData EMPTY = new TagData(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());

        public boolean isEmpty() {
            return this.members.isEmpty() && this.immediateChildIds.isEmpty() && this.immediateChildTags.isEmpty();
        }
    }
}
