// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.scanner;

import org.orecruncher.dsurround.lib.di.Cacheable;

@Cacheable
public abstract class AbstractScanner {

    public abstract void tick(long tickCount);

}
