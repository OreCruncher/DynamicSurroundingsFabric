// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;

import java.util.Collection;

public interface IResourceFinder {

    <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String path);

}