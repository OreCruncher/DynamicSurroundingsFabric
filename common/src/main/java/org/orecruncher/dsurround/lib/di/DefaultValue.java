package org.orecruncher.dsurround.lib.di;

public final class DefaultValue {
    private DefaultValue() {
    }

    public static <T> T notSet() {
        return null;
    }
}
