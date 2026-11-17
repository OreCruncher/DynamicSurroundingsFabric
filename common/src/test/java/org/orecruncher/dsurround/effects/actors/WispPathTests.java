// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WispPathTests {

    @Test
    void theSameSeedGivesTheSameWisp() {
        var a = WispPath.of(10D, -20D, 7L);
        var b = WispPath.of(10D, -20D, 7L);
        assertEquals(a.lifetime(), b.lifetime());
        assertEquals(a.lights(), b.lights());
        for (int age = 0; age < a.lifetime(); age += 97) {
            assertEquals(a.x(age), b.x(age));
            assertEquals(a.z(age), b.z(age));
            for (int i = 0; i < a.lights(); i++)
                assertEquals(a.brightness(i, age), b.brightness(i, age));
        }
    }

    @Test
    void itLivesItsSpanWithAFewLights() {
        for (long seed = 0; seed < 50; seed++) {
            var path = WispPath.of(0D, 0D, seed);
            assertTrue(path.lifetime() >= WispPath.MIN_LIFETIME && path.lifetime() <= WispPath.MAX_LIFETIME);
            assertTrue(path.lights() >= 1 && path.lights() <= 4);
        }
    }

    @Test
    void itStartsAtItsFirstHauntAndTheOthersAreAWayOff() {
        for (long seed = 0; seed < 50; seed++) {
            var path = WispPath.of(100D, 200D, seed);
            assertEquals(0, path.hauntAt(1F));
            assertEquals(100D, path.hauntX(0));
            assertEquals(200D, path.hauntZ(0));
            for (int h = 1; h < WispPath.HAUNTS; h++) {
                var distance = Math.sqrt(Mth.lengthSquared(path.hauntX(h) - 100D, path.hauntZ(h) - 200D));
                assertTrue(distance >= 12D && distance <= 24D, "haunt " + h + " at " + distance);
            }
        }
    }

    @Test
    void aLightIsDarkOutsideItsLifeAndNeverBrighterThanFull() {
        for (long seed = 0; seed < 20; seed++) {
            var path = WispPath.of(0D, 0D, seed);
            for (int i = 0; i < path.lights(); i++) {
                assertEquals(0F, path.brightness(i, 0F));
                assertEquals(0F, path.brightness(i, path.lifetime()));
                for (int age = 1; age < path.lifetime(); age++) {
                    var b = path.brightness(i, age);
                    assertTrue(b >= 0F && b <= 1F, "brightness " + b);
                }
            }
        }
    }

    @Test
    void eachLightGoesOutNowAndThen() {
        var path = WispPath.of(0D, 0D, 3L);
        for (int i = 0; i < path.lights(); i++) {
            int dark = 0;
            // Through the middle of its life, away from its first and last fades
            for (int age = 200; age < 1000; age++)
                if (path.brightness(i, age) == 0F)
                    dark++;
            assertTrue(dark > 0, "light " + i + " never went out");
            assertTrue(dark < 400, "light " + i + " was out " + dark + " ticks of 800");
        }
    }

    @Test
    void itMovesOnlyWhileItIsOut() {
        // From one tick to the next a light moves a little, unless it jumped: and then it was out
        for (long seed = 0; seed < 20; seed++) {
            var path = WispPath.of(0D, 0D, seed);
            for (int i = 0; i < path.lights(); i++) {
                for (int age = 1; age < path.lifetime(); age++) {
                    int haunt = path.hauntAt(age), lastHaunt = path.hauntAt(age - 1);
                    var step = Math.sqrt(Mth.lengthSquared(path.lightX(i, haunt, age) - path.lightX(i, lastHaunt, age - 1),
                            path.lightZ(i, haunt, age) - path.lightZ(i, lastHaunt, age - 1)));
                    if (step > 0.5D)
                        assertTrue(path.brightness(i, age) == 0F || path.brightness(i, age - 1) == 0F,
                                "seed " + seed + " light " + i + " jumped " + step + " while lit at " + age);
                }
            }
        }
    }
}
