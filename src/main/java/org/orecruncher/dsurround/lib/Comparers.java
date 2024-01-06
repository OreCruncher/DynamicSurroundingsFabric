package org.orecruncher.dsurround.lib;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;

public class Comparers {

    /**
     * Because the comparison that Identifier uses compares the path prior to namespace thus making sorting
     * for visual representation sucky.  That's a technical term.
     */
    public static final Comparator<ResourceLocation> IDENTIFIER_NATURAL_COMPARABLE = new Comparator<ResourceLocation>() {
        @Override
        public int compare(ResourceLocation o1, ResourceLocation o2) {
            int i = o1.getNamespace().compareTo(o2.getNamespace());
            if (i == 0) {
                i = o1.getPath().compareTo(o2.getPath());
            }
            return i;
        }
    };
}
