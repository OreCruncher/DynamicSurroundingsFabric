package org.orecruncher.dsurround.config;

import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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
        // Without the platform's weather hooks to look for (Fabric, and these tests), it may draw its own weather
        var subclass = new DimensionSpecialEffects.OverworldEffects() {
        };
        assertFalse(DimensionInfo.hasVanillaWeather(subclass, Set.of()));
    }

    @Test
    void aModsEffectsThatLeaveTheWeatherAloneGetParticleWeather() {
        // Regression: Stellar View replaces the overworld's effects for its sky, which turned particle weather off
        var sky = new DimensionSpecialEffects.OverworldEffects() {
            @Override
            public float getCloudHeight() {
                return 200F;
            }
        };
        assertTrue(DimensionInfo.hasVanillaWeather(sky, HOOKS));
    }

    @Test
    void aModsEffectsThatDrawOrTickWeatherAreLeftTheirWeather() {
        assertFalse(DimensionInfo.hasVanillaWeather(new DrawsWeather(), HOOKS));
        assertFalse(DimensionInfo.hasVanillaWeather(new TicksRain(), HOOKS));
        // Inherited from a mod's class in between
        assertFalse(DimensionInfo.hasVanillaWeather(new DrawsWeather() {
        }, HOOKS));
    }

    @Test
    void vanillasCloudsAreReplacedButADimensionsOwnAreLeft() {
        assertTrue(DimensionInfo.hasVanillaClouds(new DimensionSpecialEffects.OverworldEffects(), CLOUD_HOOKS));
        assertTrue(DimensionInfo.hasVanillaClouds(new DrawsWeather(), CLOUD_HOOKS), "weather of its own, vanilla's clouds");
        assertFalse(DimensionInfo.hasVanillaClouds(new DrawsClouds(), CLOUD_HOOKS));
        // Without the platform's hook to look for, a mod's effects may draw their own
        assertFalse(DimensionInfo.hasVanillaClouds(new DimensionSpecialEffects.OverworldEffects() {
        }, Set.of()));
    }

    // NeoForge's weather hooks; the test class path is vanilla's, so the effects here declare them themselves
    private static final Set<String> HOOKS = Set.of("renderSnowAndRain", "tickRain");
    // And its cloud hook
    private static final Set<String> CLOUD_HOOKS = Set.of("renderClouds");

    private static class DrawsWeather extends DimensionSpecialEffects.OverworldEffects {
        @SuppressWarnings("unused")
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
            return true;
        }
    }

    private static class TicksRain extends DimensionSpecialEffects.OverworldEffects {
        @SuppressWarnings("unused")
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            return true;
        }
    }

    private static class DrawsClouds extends DimensionSpecialEffects.OverworldEffects {
        @SuppressWarnings("unused")
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, double camX, double camY, double camZ) {
            return true;
        }
    }
}
