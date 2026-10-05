package org.orecruncher.dsurround.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Tests for the date window check behind platform.isCurrentDateInRangeOf(month, day, range).
 */
@DisplayName("PlatformFunctions")
public class PlatformFunctionsTests {

    /**
     * {target month, target day, range, today, expected}
     */
    private static Object[] row(int month, int day, int range, String today, boolean expected) {
        return new Object[]{month, day, range, today, expected};
    }

    private static Stream<DynamicTest> cases(Object[]... rows) {
        return Stream.of(rows).map(r -> {
            int month = (int) r[0], day = (int) r[1], range = (int) r[2];
            var today = LocalDate.parse((String) r[3]);
            boolean expected = (boolean) r[4];
            return dynamicTest("%02d/%02d +/- %d on %s  =>  %s".formatted(month, day, range, today, expected),
                    () -> assertEquals(expected, PlatformFunctions.isDateInRange(today, month, day, range)));
        });
    }

    @TestFactory
    @DisplayName("Windows within a year")
    Stream<DynamicTest> withinYear() {
        return cases(
                row(12, 25, 7, "2026-12-25", true),    // on the day
                row(12, 25, 7, "2026-12-18", true),    // first day of the window (inclusive)
                row(12, 25, 7, "2026-12-17", false),   // day before the window
                row(12, 25, 7, "2026-12-30", true),
                row(12, 25, 7, "2027-01-01", true),    // last day of the window (inclusive), across New Year's
                row(12, 25, 7, "2027-01-02", false),   // day after the window
                row(7, 4, 0, "2026-07-04", true),      // range 0 matches only the day itself
                row(7, 4, 0, "2026-07-05", false),
                row(10, 31, 14, "2026-09-30", false),
                row(10, 31, 31, "2026-09-30", true)
        );
    }

    @TestFactory
    @DisplayName("Windows that cross New Year's")
    Stream<DynamicTest> acrossNewYear() {
        return cases(
                row(1, 1, 3, "2026-12-29", true),      // before New Year's: next year's Jan 1
                row(1, 1, 3, "2026-12-28", false),
                row(1, 1, 3, "2027-01-04", true),      // after New Year's
                row(1, 1, 3, "2027-01-05", false),
                row(12, 31, 3, "2027-01-03", true),    // after New Year's: last year's Dec 31
                row(12, 31, 3, "2027-01-04", false),
                row(1, 3, 5, "2026-12-31", true),
                row(12, 28, 5, "2027-01-02", true)
        );
    }

    @TestFactory
    @DisplayName("Impossible dates and leap days")
    Stream<DynamicTest> specialDates() {
        return cases(
                row(13, 1, 30, "2026-12-15", false),   // month 13
                row(0, 1, 30, "2026-01-15", false),    // month 0
                row(2, 30, 30, "2026-02-15", false),   // February 30
                row(4, 31, 30, "2026-04-15", false),   // April 31
                row(2, 29, 0, "2028-02-29", true),     // leap day in a leap year
                row(2, 29, 3, "2028-03-02", true),
                row(2, 29, 3, "2026-02-28", false),    // no leap day in 2026; 2028's is too far away
                row(2, 29, 400, "2027-02-28", true)    // a window wider than a year reaches 2028's leap day
        );
    }

    @Test
    @DisplayName("Agrees with a day-by-day reference for every day of 2026-2028")
    void matchesReference() {
        int[] ranges = {0, 1, 3, 7, 14};
        int checked = 0;
        for (var today = LocalDate.of(2026, 1, 1); today.getYear() < 2029; today = today.plusDays(1)) {
            for (int range : ranges) {
                // Reference: every month/day that occurs in the window, found by walking it a day at a time
                Set<MonthDay> inWindow = new HashSet<>();
                for (var d = today.minusDays(range); !d.isAfter(today.plusDays(range)); d = d.plusDays(1))
                    inWindow.add(MonthDay.from(d));

                for (int month = 1; month <= 12; month++) {
                    for (int day = 1; day <= 31; day++) {
                        boolean expected = isValid(month, day) && inWindow.contains(MonthDay.of(month, day));
                        boolean actual = PlatformFunctions.isDateInRange(today, month, day, range);
                        if (expected != actual)
                            fail("%02d/%02d +/- %d on %s: expected %s".formatted(month, day, range, today, expected));
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 2_000_000, "checked " + checked);
    }

    private static boolean isValid(int month, int day) {
        try {
            MonthDay.of(month, day);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
