package org.orecruncher.dsurround.lib.scripting;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.*;
import java.util.List;
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

    private Object iif(final List<Object> args) {
        var flag = args.getFirst() instanceof Boolean b && b;
        return flag ? args.get(1) : args.get(2);
    }

    private boolean match(final List<Object> args) {
        return Pattern.matches(args.get(0).toString(), args.get(1).toString());
    }

    private boolean oneof(final List<Object> args) {
        var testee = args.getFirst();
        for (int i = 1; i < args.size(); i++)
            if (testee.equals(args.get(i)))
                return true;
        return false;
    }

    private boolean isBetween(final List<Object> args) {
        var value = ((Number)args.getFirst()).doubleValue();
        var min = ((Number)args.get(1)).doubleValue();
        var max = ((Number)args.get(2)).doubleValue();
        return value >= min && value <= max;
    }

    private boolean isModLoaded(final List<Object> args) {
        return Platform.isModLoaded(args.getFirst().toString());
    }

    private boolean isCurrentDateInRangeOf(final List<Object> args) {
        try {
            var month = (int)args.get(0);
            var day = (int)args.get(1);
            var dayRange = (int)args.get(2);

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