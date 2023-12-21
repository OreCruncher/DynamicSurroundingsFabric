package org.orecruncher.dsurround.lib;

import net.minecraft.util.Identifier;

import java.util.Comparator;

public class Comparers {

    /**
     * Because the comparison that Identifier uses compares the path prior to namespace thus making sorting
     * for visual representation sucky.  That's a technical term.
     */
    public static final Comparator<Identifier> IDENTIFIER_NATURAL_COMPARABLE = new Comparator<Identifier>() {
        @Override
        public int compare(Identifier o1, Identifier o2) {
            int i = o1.getNamespace().compareTo(o2.getNamespace());
            if (i == 0) {
                i = o1.getPath().compareTo(o2.getPath());
            }
            return i;
        }
    };
}
