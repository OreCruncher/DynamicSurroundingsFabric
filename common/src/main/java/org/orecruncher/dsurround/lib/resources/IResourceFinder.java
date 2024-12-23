package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;

import java.util.Collection;

public interface IResourceFinder {

    <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String path);

}