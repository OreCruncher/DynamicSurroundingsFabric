package org.orecruncher.dsurround.config.libraries.impl;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IClientTagUtilities;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagLibrary implements ITagLibrary {

    private final IClientTagUtilities tagUtilities;
    private final IModLog logger;

    public TagLibrary(IClientTagUtilities tagUtilities, IModLog logger) {
        this.tagUtilities = tagUtilities;
        this.logger = logger;
    }

    @Override
    public Stream<String> dump() {
        return null;
    }

    @Override
    public void reload(AssetLibraryEvent.ReloadEvent event) {
    }

    @Override
    public <T> Stream<TagKey<T>> streamTags(T entity) {
        return null;
    }

    @Override
    public <T> String asString(Stream<TagKey<T>> tagStream) {
        return tagStream
                .map(key -> key.id().toString())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    @Override
    public <T> boolean isIn(TagKey<T> tagKey, T entry) {
        return this.tagUtilities.isIn(tagKey, entry);
    }

    @Override
    public <T> boolean isIn(TagKey<T> tagKey, RegistryEntry<T> registryEntry) {
        return this.tagUtilities.isIn(tagKey, registryEntry);
    }

    @Override
    public <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(RegistryKey<? extends Registry<T>> registryKey) {
        return this.tagUtilities.getEntriesByTag(registryKey);
    }
}
