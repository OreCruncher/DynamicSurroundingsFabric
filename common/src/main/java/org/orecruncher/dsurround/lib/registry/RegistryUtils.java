package org.orecruncher.dsurround.lib.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public class RegistryUtils {

    @SuppressWarnings("unchecked")
    public static <T> Optional<Registry<T>> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        var dynamic = GameUtils.getRegistryManager()
                .flatMap(rm -> rm.lookup(registryKey));
        if (dynamic.isPresent()) {
            return dynamic;
        }
        return (Optional<Registry<T>>) (Optional<?>) Optional.ofNullable(BuiltInRegistries.REGISTRY.getValue(registryKey.identifier()));
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<? extends Registry<T>> registryKey, T instance) {
        return getRegistry(registryKey)
                .map(registry -> registry.getKey(instance))
                .flatMap(id -> id != null ? getRegistryEntry(registryKey, id) : Optional.empty());
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<? extends Registry<T>> registryKey, Identifier location) {
        return getRegistry(registryKey)
                .flatMap(registry -> registry.get(location));
    }
}
