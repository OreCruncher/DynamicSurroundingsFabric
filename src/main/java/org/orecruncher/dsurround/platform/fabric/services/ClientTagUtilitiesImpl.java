package org.orecruncher.dsurround.platform.fabric.services;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.platform.IClientTagUtilities;

import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

public class ClientTagUtilitiesImpl implements IClientTagUtilities {

    public <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return ClientTags.isInWithLocalFallback(tagKey, entry);
    }

    public <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return ClientTags.isInWithLocalFallback(tagKey, registryEntry);
    }

    public <T> Stream<T> getTagMembers(TagKey<T> tagKey) {
        var registryManager = GameUtils.getRegistryManager().orElseThrow();
        var registry = registryManager.get(tagKey.registry());
        return ClientTags.getOrCreateLocalTag(tagKey)
                .stream()
                .map(registry::get);
    }

    // TODO: How to include client tag references
    public <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(RegistryKey<? extends Registry<T>> registryKey) {
        var registryManager = GameUtils.getRegistryManager().orElseThrow();
        var registry = registryManager.get(registryKey);
        return registry.streamEntries()
                .flatMap(e -> e.streamTags().map(tag -> Pair.of(tag, e.value())))
                .collect(groupingBy(Pair::key, mapping(Pair::value, toSet())))
                .entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()));
    }
}
