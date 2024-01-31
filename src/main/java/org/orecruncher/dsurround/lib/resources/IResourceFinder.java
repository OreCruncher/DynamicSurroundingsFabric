package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.util.Collection;

public interface IResourceFinder<T> {

    default IPlatform getPlatform() {
        return Library.PLATFORM;
    }

    Collection<DiscoveredResource<T>> find(String assetPath);

    Collection<DiscoveredResource<T>> find(ResourceLocation resource);
}
