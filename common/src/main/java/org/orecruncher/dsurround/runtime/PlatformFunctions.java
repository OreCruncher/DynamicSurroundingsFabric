package org.orecruncher.dsurround.runtime;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.scripting.IConfigureScripting;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

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
        // Pure: the set of loaded mods does not change while the game is running, so a call with a constant mod id
        // is evaluated once when the script is compiled.
        config.function("platform.isModLoaded")
                .param(ArgType.STRING)
                .pure()
                .handler(args -> Platform.isModLoaded(args.string(0)));

        // Not pure: the current date can change during a session.
        config.function("platform.isCurrentDateInRangeOf")
                .param(ArgType.INTEGER).param(ArgType.INTEGER).param(ArgType.INTEGER)
                .handler(args -> this.isCurrentDateInRangeOf(args.integer(0), args.integer(1), args.integer(2)));
    }

    private boolean isCurrentDateInRangeOf(final int month, final int day, final int dayRange) {
        var today = LocalDate.ofInstant(this.systemClock.getUtcNow(), ZoneOffset.UTC);
        return isDateInRange(today, month, day, dayRange);
    }

    /**
     * Determines if the month and day fall within dayRange days of today, in either direction (inclusive). The
     * occurrences in the previous, current, and next year are all checked, so a window that crosses New Year's
     * works: Jan 1 +/- 3 days matches on Dec 30, and Dec 31 +/- 3 days matches on Jan 1. An impossible date
     * (month 13, February 30) never matches, and February 29 only matches around leap days.
     */
    static boolean isDateInRange(final LocalDate today, final int month, final int day, final int dayRange) {
        for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
            try {
                var target = LocalDate.of(year, month, day);
                if (Math.abs(ChronoUnit.DAYS.between(today, target)) <= dayRange)
                    return true;
            } catch (DateTimeException ignore) {
                // An impossible date, such as month 13 or February 30, or February 29 in a non-leap year
            }
        }
        return false;
    }
}