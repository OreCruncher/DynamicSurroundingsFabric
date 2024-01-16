package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableList;
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
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class ClientTagLoader {

    private final IModLog logger;
    private final Map<TagKey<?>, TagData<?>> tagCache = new Reference2ObjectOpenHashMap<>();
    private final Predicate<TagKey<?>> filter;

    public ClientTagLoader(IModLog logger) {
        this(logger, tagKey -> true);
    }

    public ClientTagLoader(IModLog logger, Predicate<TagKey<?>> filter) {
        this.logger = logger;
        this.filter = filter;
    }

    public <T> Collection<T> getMembers(TagKey<T> tagKey) {
        if (this.filter.test(tagKey))
            return this.getTagData(tagKey).members();
        return ImmutableList.of();
    }

    public void clear() {
        this.tagCache.clear();
    }

    private <T> TagData<T> getTagData(TagKey<T> tagKey) {
        // We cannot use computeIfAbsent() because tag loading is a
        // recursive process and will refer back and potentially modify the
        // tag cache.
        var data = this.tagCache.get(tagKey);
        if (data == null) {
            this.logger.debug("Loading tag files for %s", tagKey.toString());
            data = this.loadTagData(tagKey);
            this.tagCache.put(tagKey, data);
        }
        return TagData.cast(data);
    }

    // This is based on TagLoader. Inspiration from Fabric client tag API.
    private <T> TagData<T> loadTagData(TagKey<T> tagKey) {
        Set<TagEntry> entries = new HashSet<>();

        var tagFiles = ResourceUtils.findClientTagFiles(tagKey);

        for (var tagFile : tagFiles) {
            if (tagFile.replace())
                entries.clear();
            entries.addAll(tagFile.entries());
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
        var registry = RegistryUtils.getRegistry(tagKey.registry()).orElseThrow();
        var instances = new ArrayList<T>();
        for (var id : completeIds) {
            var rk = ResourceKey.create(tagKey.registry(), id);
            registry.getOptional(rk).ifPresent(instances::add);
        }

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
