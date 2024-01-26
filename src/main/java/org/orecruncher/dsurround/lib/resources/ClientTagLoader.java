package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
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

    public <T> Collection<ResourceLocation> getCompleteIds(TagKey<T> tagKey) {
        return this.getTagData(tagKey, new HashSet<>()).completeIds();
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

        Set<ResourceLocation> completeIds = new HashSet<>();

        // If we can take a shortcut by looking up tag membership in the registries, do so. It's
        // faster than scanning resources directly.
        if (this.takeShortcutLookup(tagKey)) {
            this.logger.debug(RESOURCE_LOADING, "%s - Shortcut lookup; scanning registries", tagKey);
            var registry = RegistryUtils.getRegistry(tagKey.registry()).orElseThrow();
            var entities = registry.holders()
                    .filter(h -> h.is(tagKey))
                    .map(h -> h.unwrapKey().get().location())
                    .toList();
            completeIds.addAll(entities);
        } else {
            // We never replace tag info, so we ignore and just merge in with what
            // has been discovered.
            Set<TagEntry> entries = new HashSet<>();
            var tagFiles = ResourceUtils.findClientTagFiles(tagKey);
            tagFiles.forEach(tf -> entries.addAll(tf.entries()));

            if (!entries.isEmpty()) {
                this.logger.debug(RESOURCE_LOADING, "%s - %d entries found", tagKey, entries.size());

                var lookup = new TagEntry.Lookup<ResourceLocation>() {
                    @Nullable
                    @Override
                    public ResourceLocation element(ResourceLocation id) {
                        return id;
                    }
                    @Nullable
                    @Override
                    public Collection<ResourceLocation> tag(ResourceLocation id) {
                        TagKey<?> tag = TagKey.create(tagKey.registry(), id);
                        // This will trigger recursion to generate a complete list of IDs
                        ClientTagLoader.this.logger.debug(RESOURCE_LOADING, "%s - Recurse %s", tagKey, tag);
                        var result = ClientTagLoader.this.getTagData(tag, visited).completeIds();
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
                ImmutableSet.copyOf(completeIds));
    }

    private boolean takeShortcutLookup(TagKey<?> tagKey) {
        var namespace = tagKey.location().getNamespace();
        // If it's a mod tag, we have to look in local resources.
        if (namespace.equals(Library.MOD_ID))
            return false;
        // If there is a single player world being hosted, it means that the local
        // registries are intact and a tag lookup can be taken. Otherwise, the client is
        // connected to a remote server. If it is a Minecraft tag, we can obtain the
        // information from the registry. Otherwise, do the crawl.
        return GameUtils.getMC().getSingleplayerServer() != null || namespace.equals("minecraft");
    }

    private record TagData<T>(
            Set<T> members,
            Set<ResourceLocation> completeIds) {
        private static final TagData<?> EMPTY = new TagData<>(ImmutableSet.of(), ImmutableSet.of());

        public static <T> TagData<T> empty() {
            return cast(EMPTY);
        }

        @SuppressWarnings("unchecked")
        public static <T> TagData<T> cast(TagData<?> tagData) {
            return (TagData<T>)tagData;
        }
    }
}
