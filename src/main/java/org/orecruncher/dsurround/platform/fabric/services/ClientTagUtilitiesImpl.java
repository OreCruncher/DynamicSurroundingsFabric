package org.orecruncher.dsurround.platform.fabric.services;

import net.fabricmc.fabric.api.tag.client.v1.ClientTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.platform.IClientTagUtilities;

public class ClientTagUtilitiesImpl implements IClientTagUtilities {

    public <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return ClientTags.isInWithLocalFallback(tagKey, entry);
    }

    public <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return ClientTags.isInWithLocalFallback(tagKey, registryEntry);
    }
}
