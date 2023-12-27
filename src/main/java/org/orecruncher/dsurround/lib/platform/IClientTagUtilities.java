package org.orecruncher.dsurround.lib.platform;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

public interface IClientTagUtilities {
    <T> boolean isIn(TagKey<T> tagKey, T entry);

    <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry);
}
