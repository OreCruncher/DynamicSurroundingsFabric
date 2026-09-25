package org.orecruncher.dsurround.lib;

import net.minecraft.resources.Identifier;

import java.util.Comparator;

public class Comparers {

    /**
     * Because the comparison that Identifier uses compares the path prior to namespace, thus making sorting
     * for visual representation sucky.  That's a technical term.
     */
    public static final Comparator<Identifier> IDENTIFIER_NATURAL_COMPARABLE = Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath);
}
