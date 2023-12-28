package org.orecruncher.dsurround.config.libraries.impl;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagEntry;
import net.minecraft.registry.tag.TagFile;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.TagManagerLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.orecruncher.dsurround.lib.platform.IPlatform;

import static java.util.stream.Collectors.*;

public class TagLibrary implements ITagLibrary {

    private final IPlatform platform;
    private final IModLog logger;

    private final Map<TagKey<?>, TagData> tagCache = new HashMap<>();

    public TagLibrary(IPlatform platform, IModLog logger) {
        this.platform = platform;
        this.logger = logger;
    }

    @Override
    public Stream<String> dump() {
        return this.tagCache.entrySet().stream()
                .map(kvp -> {
                    var builder = new StringBuilder();

                    builder.append("Tag: ").append(kvp.getKey().toString()).append("\n");
                    builder.append("Members [");
                    for (var e : kvp.getValue().members())
                        builder.append("\n  ").append(e.toString());
                    builder.append("\n]\nTags [");
                    for (var e : kvp.getValue().immediateChildTags())
                        builder.append("\n  ").append(e.toString());
                    builder.append("\n]\nDirect [");
                    for (var e : kvp.getValue().immediateChildIds())
                        builder.append("\n  ").append(e.toString());
                    builder.append("\n]\n");

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
                .map(key -> key.id().toString())
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
    public <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        // Check dynamic registry first - may have set by the server
        if (registryEntry.isIn(tagKey)) {
            return true;
        }

        // Fallback to data packs on the client
        return this.getObjectIdentifier(registryEntry)
                .map(id -> this.getTagData(tagKey).members().contains(id))
                .orElse(false);
    }

    @Override
    public <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(RegistryKey<? extends Registry<T>> registryKey) {
        // TODO: How to include client tag references
        var registryManager = GameUtils.getRegistryManager().orElseThrow();
        var registry = registryManager.get(registryKey);
        return registry.streamEntries()
                .flatMap(e -> e.streamTags().map(tag -> Pair.of(tag, e.value())))
                .collect(groupingBy(Pair::key, mapping(Pair::value, toSet())))
                .entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    }

    private <T> Optional<Identifier> getObjectIdentifier(RegistryEntry<T> registryEntry) {
        return registryEntry.getKey().map(RegistryKey::getValue);
    }

    private <T> Optional<RegistryEntry<T>> getRegistryEntry(TagKey<T> tagKey, T entry) {
        var maybeRegistry = getRegistry(tagKey);

        if (maybeRegistry.isEmpty() || !tagKey.isOf(maybeRegistry.get().getKey())) {
            return Optional.empty();
        }

        Registry<T> registry = maybeRegistry.get();

        return registry.getKey(entry).map(registry::entryOf);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<? extends Registry<T>> getRegistry(TagKey<T> tagKey) {
        if (GameUtils.isInGame()) {
            var maybeRegistry = GameUtils.getRegistryManager().map(rm -> rm.getOptional(tagKey.registry())).orElseThrow();
            if (maybeRegistry.isPresent())
                return maybeRegistry;
        }

        return (Optional<? extends Registry<T>>) Registries.REGISTRIES.getOrEmpty(tagKey.registry().getValue());
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
        Set<Path> tagFiles = this.getTagFiles(tagKey.registry(), tagKey.id());

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
            // This could be legitimately possible. However, if there is a type in the json, like
            // c:glass_pane vs. c:glass_panes, it could result in an empty scan.  Ask me how I
            // know.
            return TagData.EMPTY;
        }

        Set<Identifier> completeIds = new HashSet<>();
        Set<Identifier> immediateChildIds = new HashSet<>();
        Set<TagKey<?>> immediateChildTags = new HashSet<>();

        for (TagEntry tagEntry : entries) {
            tagEntry.resolve(new TagEntry.ValueGetter<>() {
                @Nullable
                @Override
                public Identifier direct(Identifier id) {
                    immediateChildIds.add(id);
                    return id;
                }

                @Nullable
                @Override
                public Collection<Identifier> tag(Identifier id) {
                    TagKey<?> tag = TagKey.of(tagKey.registry(), id);
                    immediateChildTags.add(tag);
                    // This will trigger recursion to generate a complete list of IDs
                    return getTagData(tag).members();
                }
            }, completeIds::add);
        }

        immediateChildTags.remove(tagKey);
        return new TagData(ImmutableSet.copyOf(completeIds), ImmutableSet.copyOf(immediateChildTags), ImmutableSet.copyOf(immediateChildIds));
    }

    private Set<Path> getTagFiles(RegistryKey<? extends Registry<?>> registryKey, Identifier identifier) {
        var tagType = TagManagerLoader.getPath(registryKey);
        var tagFile = "data/%s/%s/%s.json".formatted(identifier.getNamespace(), tagType, identifier.getPath());
        return this.platform.getResourcePaths(tagFile);
    }

    private record TagData(Set<Identifier> members, Set<TagKey<?>> immediateChildTags, Set<Identifier> immediateChildIds) {
        public static final TagData EMPTY = new TagData(ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());
    }
}
