package org.orecruncher.dsurround.tags;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.stream.Stream;

public class TagHelpers {

    public static <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return ClientTags.isInWithLocalFallback(tagKey, entry);
    }

    public static <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return ClientTags.isInWithLocalFallback(tagKey, registryEntry);
    }

    public static <T> Stream<Pair<T, Stream<TagKey<T>>>> getTagGroup(RegistryKey<? extends Registry<T>> registryKey) {
        return GameUtils.getWorld().getRegistryManager().get(registryKey).streamEntries()
                .map(reference -> Pair.of(reference.value(), reference.streamTags()));
    }
}
