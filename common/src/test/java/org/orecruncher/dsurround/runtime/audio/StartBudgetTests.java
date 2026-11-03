// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StartBudgetTests {

    private long now;

    private StartBudget budget() {
        // 2 ms every 50 ms, as sounds starting have
        return new StartBudget(50_000_000L, 2_000_000L, () -> this.now);
    }

    @Test
    void startsCalculateUntilTheWindowsBudgetIsSpent() {
        var budget = budget();
        for (int i = 0; i < 4; i++) {
            assertTrue(budget.mayCalculate(), "start " + i);
            budget.spend(500_000L);
            this.now += 1_000_000L;
        }
        // 2 ms spent in this window: the rest wait for the workers
        assertFalse(budget.mayCalculate());
        this.now += 10_000_000L;
        assertFalse(budget.mayCalculate());
    }

    @Test
    void aNewWindowStartsAfresh() {
        var budget = budget();
        assertTrue(budget.mayCalculate());
        budget.spend(5_000_000L);
        assertFalse(budget.mayCalculate());
        this.now += 50_000_000L;
        assertTrue(budget.mayCalculate());
    }

    @Test
    void oneLongStartIsAllowedButSpendsTheWindow() {
        // A start isn't stopped part way: the one that goes over still finishes, and the next waits
        var budget = budget();
        assertTrue(budget.mayCalculate());
        budget.spend(1_900_000L);
        assertTrue(budget.mayCalculate());
        budget.spend(4_000_000L);
        assertFalse(budget.mayCalculate());
    }

    @Test
    void theFirstStartOpensAWindowWhateverTheClockSays() {
        this.now = -123_456_789L;
        var budget = budget();
        assertTrue(budget.mayCalculate());
        budget.spend(2_000_000L);
        assertFalse(budget.mayCalculate());
    }
}
