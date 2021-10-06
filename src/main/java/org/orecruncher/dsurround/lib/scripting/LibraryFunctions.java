package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.FrameworkUtils;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Library functions exposed via the JavaScript engine.  They are not directly used by code.
 */
@SuppressWarnings("unused")
public final class LibraryFunctions {

    public Object iif(final boolean flag, @Nullable final Object trueResult, @Nullable final Object falseResult) {
        return flag ? trueResult : falseResult;
    }

    public boolean match(final String pattern, final String subject) {
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(subject);
        return Pattern.matches(pattern, subject);
    }

    public boolean oneof(final Object testee, final Object... possibles) {
        Objects.requireNonNull(testee);
        Objects.requireNonNull(possibles);
        for (final Object obj : possibles)
            if (testee.equals(obj))
                return true;
        return false;
    }

    public boolean isBetween(final double value, final double min, final double max) {
        return value >= min && value <= max;
    }

    public boolean isModLoaded(final String mod) {
        return FrameworkUtils.isModLoaded(mod);
    }
}