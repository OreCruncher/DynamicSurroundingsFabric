// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.data.ActorConfig;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActorEntryTests {

    private static ActorEntry entry(String chance, Object scriptResult) {
        IConditionEvaluator evaluator = Fakes.of(IConditionEvaluator.class, Map.of("eval", args -> scriptResult));
        var config = new ActorConfig(ActorKind.DUST_DEVIL.getId(), Script.TRUE, new Script(chance));
        return new ActorEntry(ActorKind.DUST_DEVIL, config, evaluator);
    }

    @Test
    void aConstantChanceIsClampedToAShare() {
        assertEquals(0.25F, entry("0.25", null).chance());
        assertEquals(1F, entry("3", null).chance());
        assertEquals(0F, entry("-1", null).chance());
    }

    @Test
    void aConstantThatIsNotAFiniteNumberIsNoChance() {
        // A NaN chance would pass every roll, so the actor would start at every chance it got
        assertEquals(0F, entry("NaN", null).chance());
        assertEquals(0F, entry("Infinity", null).chance());
    }

    @Test
    void aScriptGivingNoFiniteNumberIsNoChance() {
        assertEquals(0F, entry("0 / 0", Double.NaN).chance());
        assertEquals(0F, entry("1 / 0", Double.POSITIVE_INFINITY).chance());
        assertEquals(0F, entry("weather.isRaining()", true).chance());
        assertEquals(0.5F, entry("weather.getRainIntensity()", 0.5).chance());
    }
}
