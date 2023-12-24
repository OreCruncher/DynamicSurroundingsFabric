package org.orecruncher.dsurround.tags;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.platform.Services;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagHelpers {

    public static <T> String asString(Stream<TagKey<T>> tagStream) {
        return tagStream
                .map(key -> key.id().toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    public static <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return Services.TAG_UTILITIES.isIn(tagKey, entry);
    }

    public static <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return Services.TAG_UTILITIES.isIn(tagKey, registryEntry);
    }

    public static <T> Stream<Pair<T, Stream<TagKey<T>>>> getTagGroup(RegistryKey<? extends Registry<T>> registryKey) {
        return GameUtils.getWorld().getRegistryManager().get(registryKey).streamEntries()
                .map(reference -> Pair.of(reference.value(), reference.streamTags()));
    }
}
