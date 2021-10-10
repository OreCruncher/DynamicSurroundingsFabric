package org.orecruncher.dsurround.lib;

import java.util.regex.Pattern;

public final class PatternValidation {

    /**
     * BlockState Property names
     */
    public static Pattern BLOCKSTATE_PROPERTY_NAME = Pattern.compile("^[a-z0-9_]+$");

    public static Pattern HTML_COLOR_ENCODING = Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
}
