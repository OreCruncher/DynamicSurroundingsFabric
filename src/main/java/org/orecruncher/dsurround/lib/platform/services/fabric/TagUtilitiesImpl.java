package org.orecruncher.dsurround.lib.platform.services.fabric;

import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.platform.ITagUtilities;

public class TagUtilitiesImpl implements ITagUtilities {

    public <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return ClientTags.isInWithLocalFallback(tagKey, entry);
    }

    public <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return ClientTags.isInWithLocalFallback(tagKey, registryEntry);
    }
}
