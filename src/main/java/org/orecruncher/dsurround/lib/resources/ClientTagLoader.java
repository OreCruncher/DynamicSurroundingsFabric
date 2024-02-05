package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.MinecraftServerType;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

@SuppressWarnings("unused")
public class ClientTagLoader {

    private final ResourceUtilities resourceUtilities;
    private final IModLog logger;
    private final ISystemClock systemClock;
    private final Map<TagKey<?>, TagData<?>> tagCache = new Reference2ObjectOpenHashMap<>(256);

    private MinecraftServerType serverType;

    public ClientTagLoader(ResourceUtilities resourceUtilities, IModLog logger, ISystemClock systemClock) {
        this.resourceUtilities = resourceUtilities;
        this.logger = logger;
        this.systemClock = systemClock;
        this.serverType = MinecraftServerType.VANILLA;
    }

    public Collection<ResourceLocation> getMembers(TagKey<?> tagKey) {
        return this.getTagData(tagKey, new HashSet<>()).members();
    }

    public <T> Collection<ResourceLocation> getCompleteIds(TagKey<T> tagKey) {
        return this.getTagData(tagKey, new HashSet<>()).members();
    }

    public void clear() {
        this.tagCache.clear();
    }

    public void setServerType(MinecraftServerType serverType) {
        this.serverType = serverType;
    }

    private <T> TagData<T> getTagData(TagKey<T> tagKey, Set<TagKey<?>> visited) {
        // We cannot use computeIfAbsent() because tag loading is a
        // recursive process and will refer back and potentially modify the
        // tag cache.
        var data = this.tagCache.get(tagKey);
        if (data == null) {
            // If we already visited, there is a circular reference. Return an empty
            // tag set. This tag is already being processed up the recursion stack,
            // so we can prune.
            if (visited.contains(tagKey)) {
                this.logger.debug(RESOURCE_LOADING, "%s - Previously encountered; skipping", tagKey);
                return TagData.empty();
            }
            visited.add(tagKey);
            this.logger.debug(RESOURCE_LOADING, "%s - Loading tag files", tagKey);
            data = this.loadTagData(tagKey, visited);
            this.logger.debug(RESOURCE_LOADING, "%s - Caching results; total of %d members", tagKey, data.members().size());
            this.tagCache.put(tagKey, data);
        } else {
            this.logger.debug(RESOURCE_LOADING, "%s - Already in cache; total of %d members", tagKey, data.members().size());
        }
        return TagData.cast(data);
    }

    // This is based on TagLoader. Inspiration from Fabric client tag API.
    private <T> TagData<T> loadTagData(TagKey<T> tagKey, Set<TagKey<?>> visited) {

        var completeIds = new HashSet<ResourceLocation>();

        // If we can take a shortcut by looking up tag membership in the registries, do so. It's
        // faster than scanning resources directly.
        var registry = RegistryUtils.getRegistry(tagKey.registry()).orElseThrow();
        var holderSet = this.shortcutLookup(tagKey, registry);
        if (holderSet.isPresent()) {
            var data = holderSet.get();
            this.logger.debug(RESOURCE_LOADING, "%s - Shortcut lookup", tagKey);
            for (var holder : data) {
                var key = holder.unwrapKey();
                key.ifPresent(tResourceKey -> completeIds.add(tResourceKey.location()));
            }
        } else {
            // We never replace tag info, so we ignore and just merge in with what
            // has been discovered.
            Set<TagEntry> entries = new HashSet<>();

            var stopwatch = this.systemClock.getStopwatch();
            var tagFiles = this.resourceUtilities.findClientTagFiles(tagKey);
            this.logger.debug(RESOURCE_LOADING, "[%s] Find client tags took %dmillis", tagKey, stopwatch.elapsed(TimeUnit.MILLISECONDS));

            tagFiles.forEach(tf -> entries.addAll(tf.entries()));

            if (!entries.isEmpty()) {
                this.logger.debug(RESOURCE_LOADING, "%s - %d entries found", tagKey, entries.size());

                var lookup = new TagEntry.Lookup<ResourceLocation>() {
                    @Override
                    public @NotNull ResourceLocation element(@NotNull ResourceLocation id) {
                        return id;
                    }
                    @Nullable
                    @Override
                    public Collection<ResourceLocation> tag(@NotNull ResourceLocation id) {
                        TagKey<?> tag = TagKey.create(tagKey.registry(), id);
                        // This will trigger recursion to generate a complete list of IDs
                        ClientTagLoader.this.logger.debug(RESOURCE_LOADING, "%s - Recurse %s", tagKey, tag);
                        var result = ClientTagLoader.this.getTagData(tag, visited).members();
                        ClientTagLoader.this.logger.debug(RESOURCE_LOADING, "%s - Completed recursion %s", tagKey, tag);
                        return result;
                    }
                };

                for (TagEntry tagEntry : entries) {
                    tagEntry.build(lookup, completeIds::add);
                }
            }
        }

        if (completeIds.isEmpty()) {
            // This could be legitimately possible. However, if there is a typo in the json, like
            // c:glass_pane vs. c:glass_panes, it could result in an empty scan.  Ask me how I
            // know.
            this.logger.debug(RESOURCE_LOADING, "%s - Tag is empty", tagKey);
            return TagData.empty();
        }

        this.logger.debug(RESOURCE_LOADING, "%s - %d direct instances", tagKey, completeIds.size());

        return new TagData<>(ImmutableSet.copyOf(completeIds));
    }

    private <T> Optional<Iterable<Holder<T>>> shortcutLookup(TagKey<T> tagKey, Registry<T> registry) {
        var namespace = tagKey.location().getNamespace();
        // If its one of our tags, we always do the long lookup. They aren't really tags.
        if (namespace.equals(Library.MOD_ID))
            return Optional.empty();
        // If it is a modded server, or the namespace is minecraft, tag information should be
        // present in the local registries.
        if (this.serverType.isModded() || "minecraft".equals(namespace)) {
            var holderSet = registry.getTag(tagKey);
            if (holderSet.isPresent())
                return Optional.of(holderSet.get());
            return Optional.of(ImmutableList.of());
        }
        // We are not connected to a modded server. Do a slow scan.
        return Optional.empty();
    }

    private record TagData<T>(Set<ResourceLocation> members) {
        private static final TagData<?> EMPTY = new TagData<>(ImmutableSet.of());

        public static <T> TagData<T> empty() {
            return cast(EMPTY);
        }

        @SuppressWarnings("unchecked")
        public static <T> TagData<T> cast(TagData<?> tagData) {
            return (TagData<T>)tagData;
        }
    }
}