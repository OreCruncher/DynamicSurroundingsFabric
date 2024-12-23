package org.orecruncher.dsurround.lib;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;

public class Comparers {

    /**
     * Because the comparison that Identifier uses compares the path prior to namespace, thus making sorting
     * for visual representation sucky.  That's a technical term.
     */
    public static final Comparator<ResourceLocation> IDENTIFIER_NATURAL_COMPARABLE = Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath);
}
