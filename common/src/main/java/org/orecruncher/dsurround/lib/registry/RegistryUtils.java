package org.orecruncher.dsurround.lib.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public class RegistryUtils {

    @SuppressWarnings("unchecked")
    public static <T> Optional<Registry<T>> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        var registry = GameUtils.getRegistryManager()
                .flatMap(rm -> rm.get(registryKey));
        if (registry.isPresent()) {
            return Optional.of(registry.get().value());
        }

        // TODO: Need to validate
        var r2 = (Holder.Reference<? extends Registry<T>>) BuiltInRegistries.REGISTRY.get(registryKey.identifier()).orElseThrow();
        return Optional.of(r2.value());
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<Registry<T>> registryKey, T instance) {
        return getRegistry(registryKey)
                .flatMap(r -> r.get(r.getId(instance)));
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<Registry<T>> registryKey, Identifier location) {
        ResourceKey<T> rk = ResourceKey.create(registryKey, location);
        return getRegistry(registryKey)
                .flatMap(registry -> registry.get(rk));
    }
}
