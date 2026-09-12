package org.orecruncher.dsurround.lib.scripting;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.*;
import java.util.regex.Pattern;

/**
 * Library functions exposed via the JavaScript engine.  They are not directly used by code.
 */
@Cacheable
public final class LibraryFunctions implements IConfigureScripting {

    private final ISystemClock systemClock;

    public LibraryFunctions(ISystemClock systemClock) {
        this.systemClock = systemClock;
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction("lib.iif", 3, this::iif);
        config.defineFunction("lib.match", 2, this::match);
        config.defineFunction("lib.oneof", -2, this::oneof);
        config.defineFunction("lib.isBetween", 3, this::isBetween);
        config.defineFunction("lib.isModLoaded", 1, this::isModLoaded);
        config.defineFunction("lib.isCurrentDateInRangeOf", 3, this::isCurrentDateInRangeOf);
    }

    private Object iif(final Object[] args) {
        var flag = ScriptHelpers.toBoolean(args[0]);
        return flag ? args[1] : args[2];
    }

    private boolean match(final Object[] args) {
        return Pattern.matches(args[0].toString(), args[1].toString());
    }

    private boolean oneof(final Object[] args) {
        var testee = args[0];
        for (int i = 1; i < args.length; i++)
            if (testee.equals(args[i]))
                return true;
        return false;
    }

    private boolean isBetween(final Object[] args) {
        var value = ScriptHelpers.toDouble(args[0]);
        var min = ScriptHelpers.toDouble(args[1]);
        var max = ScriptHelpers.toDouble(args[2]);
        return value >= min && value <= max;
    }

    private boolean isModLoaded(final Object[] args) {
        return Platform.isModLoaded(args[0].toString());
    }

    private boolean isCurrentDateInRangeOf(final Object[] args) {
        try {
            var month = ScriptHelpers.toInteger(args[0]);
            var day = ScriptHelpers.toInteger(args[1]);
            var dayRange = ScriptHelpers.toInteger(args[2]);

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