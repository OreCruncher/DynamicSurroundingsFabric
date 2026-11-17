// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoundConfigDefaultsTests {

    @Test
    void aSoundWithoutAPolicyDefaultsToFullVolumeAndNoAction() {
        var policy = SoundConfigDefaults.forSound(Identifier.fromNamespaceAndPath("test", "not_shipped"));
        assertEquals(SoundConfigDefaults.Policy.NONE, policy);
        assertEquals(100, policy.volumeScale());
        assertEquals(SoundAction.DEFAULT, policy.action());
    }

    @Test
    void aShippedPolicyIsFoundBySound() {
        var policy = SoundConfigDefaults.forSound(Identifier.parse("minecraft:block.water.ambient"));
        assertEquals(SoundAction.BLOCK, policy.action());
        assertEquals(100, policy.volumeScale());
    }

    // A policy equal to the plain default would be dropped as soundconfig.json is saved, so seeding it would do nothing
    @Test
    void everyShippedPolicyDiffersFromThePlainDefault() {
        var entries = SoundConfigDefaults.entries();
        assertFalse(entries.isEmpty());
        for (var entry : entries)
            assertTrue(entry.isNotDefault(), entry.soundEventId.toString());
    }

    @Test
    void seedingAndLookupAgree() {
        for (var entry : SoundConfigDefaults.entries()) {
            var policy = SoundConfigDefaults.forSound(entry.soundEventId);
            assertEquals(policy.action(), entry.action, entry.soundEventId.toString());
            assertEquals(policy.volumeScale(), entry.volumeScale, entry.soundEventId.toString());
        }
    }

    @Test
    void seedingGivesFreshEntries() {
        var first = SoundConfigDefaults.entries().getFirst();
        first.volumeScale = 0;
        assertNotEquals(0, SoundConfigDefaults.entries().getFirst().volumeScale);
    }
}
