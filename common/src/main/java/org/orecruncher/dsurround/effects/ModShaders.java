package org.orecruncher.dsurround.effects;

import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.frost.FrostRenderer;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;
import org.orecruncher.dsurround.effects.particles.PointLights;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;

import java.util.List;
import java.util.stream.Stream;

/**
 * The mod's render pipelines. MixinShaderManager compiles each of these once the game's shaders have reloaded; each
 * is held by the effect drawn with it.
 */
public final class ModShaders {

    public static final List<ModShader> SHADERS = Stream.concat(Stream.of(
                    SoftParticles.SHADER,
                    SoftParticles.FLAME_SHADER,
                    AuroraRenderer.SHADER,
                    PointLights.SHADER),
            Stream.of(PrecipitationShaders.SHADERS, FrostRenderer.SHADERS, GroundFogVolume.SHADERS).flatMap(List::stream)).toList();

    private ModShaders() {
    }
}
