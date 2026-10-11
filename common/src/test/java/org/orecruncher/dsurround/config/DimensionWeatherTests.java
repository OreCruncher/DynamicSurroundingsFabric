package org.orecruncher.dsurround.config;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the guess at whether a dimension draws its own weather, which decides whether particle weather is used
 * there when no dimension rule says.
 */
public class DimensionWeatherTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void vanillasDimensionsGetParticleWeather() {
        assertTrue(DimensionInfo.hasVanillaWeather(new DimensionSpecialEffects.OverworldEffects()));
        assertTrue(DimensionInfo.hasVanillaWeather(new DimensionSpecialEffects.NetherEffects()));
        assertTrue(DimensionInfo.hasVanillaWeather(new DimensionSpecialEffects.EndEffects()));
    }

    @Test
    void aModsOwnEffectsAreLeftTheirWeather() {
        var modded = new DimensionSpecialEffects(192F, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
            @Override
            public @NotNull Vec3 getBrightnessDependentFogColor(@NotNull Vec3 color, float brightness) {
                return color;
            }

            @Override
            public boolean isFoggyAt(int x, int z) {
                return false;
            }
        };
        assertFalse(DimensionInfo.hasVanillaWeather(modded));
    }

    @Test
    void aModsSubclassOfVanillasEffectsCountsAsItsOwn() {
        // It may override how the weather is drawn
        var subclass = new DimensionSpecialEffects.OverworldEffects() {
        };
        assertFalse(DimensionInfo.hasVanillaWeather(subclass));
    }
}
