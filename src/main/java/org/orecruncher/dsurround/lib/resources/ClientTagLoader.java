package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import java.util.*;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

@SuppressWarnings("unused")
public class ClientTagLoader {

    private final IModLog logger;
    private final Map<TagKey<?>, TagData<?>> tagCache = new Reference2ObjectOpenHashMap<>();

    public ClientTagLoader(IModLog logger) {
        this.logger = logger;
    }

    public <T> Collection<T> getMembers(TagKey<T> tagKey) {
        return this.getTagData(tagKey, new HashSet<>()).members();
    }

    public void clear() {
        this.tagCache.clear();
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
        Set<TagEntry> entries = new HashSet<>();

        var tagFiles = ResourceUtils.findClientTagFiles(tagKey);

        for (var tagFile : tagFiles) {
            if (tagFile.replace()) {
                this.logger.debug(RESOURCE_LOADING, "%s - Replacing content of tags", tagKey);
                entries.clear();
            }
            entries.addAll(tagFile.entries());
        }

        if (entries.isEmpty()) {
            // This could be legitimately possible. However, if there is a typo in the json, like
            // c:glass_pane vs. c:glass_panes, it could result in an empty scan.  Ask me how I
            // know.
            this.logger.debug(RESOURCE_LOADING, "%s - Tag file is empty", tagKey);
            return TagData.empty();
        }

        this.logger.debug(RESOURCE_LOADING, "%s - %d entries found", tagKey, entries.size());

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
                    ClientTagLoader.this.logger.debug(RESOURCE_LOADING, "%s - Recurse %s", tagKey, tag);
                    var result = getTagData(tag, visited).completeIds();
                    ClientTagLoader.this.logger.debug(RESOURCE_LOADING, "%s - Completed recursion %s", tagKey, tag);
                    return result;
                }
            }, completeIds::add);
        }

        // Make sure the current tag is not in the immediate list
        immediateChildTags.remove(tagKey);

        // Manifest the completeId list into object instances for identity lookup
        var registry = RegistryUtils.getRegistry(tagKey.registry()).orElseThrow();
        var instances = new ArrayList<T>();
        for (var id : completeIds) {
            var rk = ResourceKey.create(tagKey.registry(), id);
            registry.getOptional(rk).ifPresent(instances::add);
        }

        this.logger.debug(RESOURCE_LOADING, "%s - %d direct instances", tagKey, instances.size());

        return new TagData<>(
                new ReferenceOpenHashSet<>(instances),
                ImmutableSet.copyOf(completeIds),
                ImmutableSet.copyOf(immediateChildTags),
                ImmutableSet.copyOf(immediateChildIds));
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
    }
}
