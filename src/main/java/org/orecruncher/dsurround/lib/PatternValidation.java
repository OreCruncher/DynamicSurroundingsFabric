package org.orecruncher.dsurround.lib;

import java.util.regex.Pattern;

public final class PatternValidation {

    /**
     * BlockState Property names
     */
    public static Pattern BLOCKSTATE_PROPERTY_NAME = Pattern.compile("^[a-z0-9_]+$");
}
