package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against particle methods that are meant to override vanilla but no longer do, which happens silently when
 * a mapping or version change renames the vanilla method.
 */
public class ParticleOverrideTests {

    private static Method inheritedMethod(Class<?> type, String name, Class<?>... parameters) {
        for (var c = type.getSuperclass(); c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void assertOverrides(Class<?> type, String name, Class<?>... parameters) {
        assertDoesNotThrow(() -> type.getDeclaredMethod(name, parameters), type.getSimpleName() + " should declare " + name);
        assertNotNull(inheritedMethod(type, name, parameters), type.getSimpleName() + "." + name + " doesn't override anything");
    }

    @Test
    void frostBreathOverridesQuadSize() {
        // Regression: the size fade-in was named getSize, an older name for getQuadSize, so it was never called
        assertOverrides(FrostBreathParticle.class, "getQuadSize", float.class);
        assertThrows(NoSuchMethodException.class, () -> FrostBreathParticle.class.getDeclaredMethod("getSize", float.class));
    }

    @Test
    void particleTicksOverrideVanilla() {
        assertOverrides(FrostBreathParticle.class, "tick");
        assertOverrides(FireflyParticle.class, "tick");
        assertOverrides(WaterRippleParticle.class, "tick");
        assertOverrides(WaterfallMist.class, "tick");
        assertOverrides(SteamPuff.class, "tick");
        assertOverrides(FlameTongue.class, "tick");
        assertOverrides(SmokePuff.class, "tick");
        assertOverrides(FlameSpark.class, "tick");
        assertOverrides(SoftPuffParticle.class, "tick");
        assertOverrides(WaterFoam.class, "tick");
    }

    @Test
    void puffsAndFoamOverrideSizeAndRendering() {
        // Puffs of mist and steam, and foam, grow over their life and set their fade (and foam its tilt) as they are
        // drawn: the puffs through the class they share
        for (var type : new Class<?>[]{SoftPuffParticle.class, WaterFoam.class}) {
            assertOverrides(type, "getQuadSize", float.class);
            assertOverrides(type, "render", VertexConsumer.class, Camera.class, float.class);
        }
        // Foam reads its light once a tick, not every frame
        assertOverrides(WaterFoam.class, "getLightColor", float.class);
        assertOverrides(SoftPuffParticle.class, "getRenderType");
        assertEquals(SoftPuffParticle.class, WaterfallMist.class.getSuperclass());
        assertEquals(SoftPuffParticle.class, SteamPuff.class.getSuperclass());
        assertEquals(SoftPuffParticle.class, SmokePuff.class.getSuperclass());
        assertEquals(SoftPuffParticle.class, FlameTongue.class.getSuperclass());
    }

    @Test
    void flameDrawsItsOwnWay() {
        // It flickers, sets its color as it is drawn, lights itself, and is drawn adding its light
        assertOverrides(FlameTongue.class, "getQuadSize", float.class);
        assertOverrides(FlameTongue.class, "render", VertexConsumer.class, Camera.class, float.class);
        assertOverrides(FlameTongue.class, "getLightColor", float.class);
        assertOverrides(FlameTongue.class, "getRenderType");
    }

    @Test
    void sparkDrawsItsOwnWay() {
        // Drawn as a streak along its path, lit by itself, adding its light as the flames do
        assertOverrides(FlameSpark.class, "render", VertexConsumer.class, Camera.class, float.class);
        assertOverrides(FlameSpark.class, "getLightColor", float.class);
        assertOverrides(FlameSpark.class, "getRenderType");
    }

    @Test
    void fireflyDrawsItsOwnWay() {
        // Its halo and light are added as it's drawn, in its own render type, which draws the lights
        assertOverrides(FireflyParticle.class, "render", VertexConsumer.class, Camera.class, float.class);
        assertOverrides(FireflyParticle.class, "getRenderType");
    }

    @Test
    void particleRenderingOverridesVanilla() {
        assertOverrides(FireflyParticle.class, "getLightColor", float.class);
        assertOverrides(FireflyParticle.class, "move", double.class, double.class, double.class);
        assertOverrides(WaterRippleParticle.class, "getLightColor", float.class);
    }
}
