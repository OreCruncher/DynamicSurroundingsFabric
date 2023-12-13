package org.orecruncher.dsurround.tags;

import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

public class TagHelpers {

    public static <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return ClientTags.isInWithLocalFallback(tagKey, entry);
    }

    public static <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return ClientTags.isInWithLocalFallback(tagKey, registryEntry);
    }
}
