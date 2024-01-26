package org.orecruncher.dsurround.lib.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public class RegistryUtils {

    @SuppressWarnings("unchecked")
    public static <T> Optional<Registry<T>> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        return GameUtils.getRegistryManager()
                .flatMap(rm -> rm.registry(registryKey))
                .or(() -> (Optional<Registry<T>>) BuiltInRegistries.REGISTRY.getOptional(registryKey.location()));
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<Registry<T>> registryKey, T instance) {
        return getRegistry(registryKey)
                .flatMap(r -> r.getHolder(r.getId(instance)));
    }

    public static <T> Optional<Holder.Reference<T>> getRegistryEntry(ResourceKey<Registry<T>> registryKey, ResourceLocation location) {
        ResourceKey<T> rk = ResourceKey.create(registryKey, location);
        return getRegistry(registryKey)
                .flatMap(registry -> registry.getHolder(rk));
    }
}
