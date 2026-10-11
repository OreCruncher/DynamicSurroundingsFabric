package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;

import java.util.List;

/**
 * The mod's core shaders. Each platform has its own API for registering shaders, and registers each of these with
 * it; each is held by the effect drawn with it.
 */
public final class ModShaders {

    public static final List<ModShader> SHADERS = List.of(
            SoftParticles.SHADER,
            AuroraRenderer.SHADER,
            PointLights.SHADER,
            PrecipitationShaders.DROPS,
            PrecipitationShaders.DAMP_SPOTS);

    private ModShaders() {
    }
}
