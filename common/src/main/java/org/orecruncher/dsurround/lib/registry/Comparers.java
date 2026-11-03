// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.registry;

import net.minecraft.resources.Identifier;

import java.util.Comparator;

public class Comparers {

    /**
     * Because the comparison that Identifier uses compares the path prior to namespace, thus making sorting
     * for visual representation sucky.  That's a technical term.
     */
    public static final Comparator<Identifier> IDENTIFIER_NATURAL_COMPARABLE = Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath);
}
