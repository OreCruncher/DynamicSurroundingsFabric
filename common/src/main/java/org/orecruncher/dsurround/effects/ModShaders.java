package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;

import java.util.List;

/**
 * The mod's render pipelines. MixinShaderManager compiles each of these once the game's shaders have reloaded; each
 * is held by the effect drawn with it.
 */
public final class ModShaders {

    public static final List<ModShader> SHADERS = List.of(
            SoftParticles.SHADER,
            SoftParticles.FLAME_SHADER,
            AuroraRenderer.SHADER,
            PointLights.SHADER);

    private ModShaders() {
    }
}
