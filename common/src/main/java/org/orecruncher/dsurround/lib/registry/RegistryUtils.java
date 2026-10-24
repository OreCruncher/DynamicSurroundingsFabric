package org.orecruncher.dsurround.lib.registry;

import com.google.common.base.Preconditions;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Optional;

public class RegistryUtils {

    public static <T> Optional<? extends Registry<T>> getRegistry(TagKey<T> tagKey) {
        Preconditions.checkNotNull(tagKey);
        return getRegistry(tagKey.registry());
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<? extends Registry<T>> getRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        Preconditions.checkNotNull(registryKey);
        var registry = GameUtils.getRegistryManager()
                .flatMap(rm -> rm.lookup(registryKey));

        if (registry.isPresent()) {
            return registry;
        }

        return (Optional<? extends Registry<T>>) BuiltInRegistries.REGISTRY.getOptional(registryKey.identifier());
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
