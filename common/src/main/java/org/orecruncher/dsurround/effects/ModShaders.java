package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.particles.FireflyLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;

import java.util.List;

/**
 * The mod's core shaders. Each platform has its own API for registering shaders, and registers each of these with
 * it; each is held by the effect drawn with it.
 */
public final class ModShaders {

    public static final List<ModShader> SHADERS = List.of(
            SoftParticles.SHADER,
            AuroraRenderer.SHADER,
            FireflyLights.SHADER);

    private ModShaders() {
    }
}
