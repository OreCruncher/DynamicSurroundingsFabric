package org.orecruncher.dsurround.lib.platform;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

import java.util.Set;
import java.util.stream.Stream;

public interface IClientTagUtilities {
    <T> boolean isIn(TagKey<T> tagKey, T entry);

    <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry);

    <T> Stream<T> getTagMembers(TagKey<T> tagKey);

    <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(RegistryKey<? extends Registry<T>> registryKey);
}
