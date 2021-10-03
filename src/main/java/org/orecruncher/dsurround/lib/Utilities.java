package org.orecruncher.dsurround.lib;

import org.apache.commons.lang3.StringUtils;

public final class Utilities {

    public static int[] splitToInts(final String str, final char splitChar) {

        final String[] tokens = StringUtils.split(str, splitChar);
        if (tokens == null || tokens.length == 0)
            return new int[0];

        final int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Integer.parseInt(tokens[i]);
        }

        return result;
    }
}
