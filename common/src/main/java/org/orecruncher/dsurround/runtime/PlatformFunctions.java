package org.orecruncher.dsurround.runtime;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.scripting.IConfigureScripting;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Extension functions defined by application logic as needed.
 */
public final class PlatformFunctions implements IConfigureScripting {

    private final ISystemClock systemClock;

    public PlatformFunctions(ISystemClock systemClock) {
        this.systemClock = systemClock;
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction("platform.isModLoaded", 1, this::isModLoaded);
        config.defineFunction("platform.isCurrentDateInRangeOf", 3, this::isCurrentDateInRangeOf);
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