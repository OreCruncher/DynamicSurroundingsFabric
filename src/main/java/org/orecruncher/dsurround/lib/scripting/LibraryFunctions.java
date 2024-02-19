package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.*;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Library functions exposed via the JavaScript engine.  They are not directly used by code.
 */
@SuppressWarnings("unused")
@Cacheable
public final class LibraryFunctions {

    private final IPlatform platform;
    private final ISystemClock systemClock;

    public LibraryFunctions(IPlatform platform, ISystemClock systemClock) {
        this.platform = platform;
        this.systemClock = systemClock;
    }

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
        return this.platform.isModLoaded(mod);
    }

    public boolean isCurrentDateInRangeOf(int month, int day, int dayRange) {
        try {
            // Get the current Utc time. Assume the test date is the same year.
            var theNow = LocalDate.ofInstant(this.systemClock.getUtcNow(), ZoneOffset.UTC);
            var testDate = LocalDate.of(theNow.getYear(), month, day);

            // If the test date is before the time window, it means the range is in the future
            var begin = theNow.minusDays(dayRange);
            if (begin.isAfter(testDate))
                return false;

            // So the test date is after the beginning of the window. See if it is before
            // the end of the window. If so, it's in range.
            var end = theNow.plusDays(dayRange);
            if (!end.isBefore(testDate))
                return true;

            // So the range looks like it is in the past. It's possible that we are dealing with an end of year thing
            // like new years. Handle that by adding one year to the test date and re-evaluate.
            testDate = testDate.plusYears(1);
            return !(begin.isAfter(testDate) || end.isBefore(testDate));
        } catch (Throwable ignore) {
        }
        return false;
    }
}