package org.orecruncher.dsurround.lib;

import java.util.regex.Pattern;

public class CodecHelpers {

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

    public static boolean isValidColorPattern(String color) {
        return COLOR_PATTERN.matcher(color).matches();
    }


}
