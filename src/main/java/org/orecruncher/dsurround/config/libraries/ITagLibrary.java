package org.orecruncher.dsurround.config.libraries;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.Set;
import java.util.stream.Stream;

public interface ITagLibrary extends ILibrary {

    <T> String asString(Stream<TagKey<T>> tagStream);

    <T> boolean isIn(TagKey<T> tagKey, T entry);

    <T> boolean isIn(TagKey<T> tagKey, Holder<T> registryEntry);

    <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(ResourceKey<? extends Registry<T>> registry);

    <T> Stream<TagKey<T>> streamTags(Holder<T> registryEntry);
}
