package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.effects.aurora.AuroraRenderer;
import org.orecruncher.dsurround.effects.particles.SoftParticles;

import java.util.List;
import java.util.function.Consumer;

/**
 * The mod's core shaders. Each platform has its own API for registering shaders, and registers each of these with
 * it. A shader that fails to load is reported through {@code onFailed}, so the game still loads without it.
 */
public final class ModShaders {

    public record Definition(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> onLoaded, Consumer<Throwable> onFailed) {
    }

    public static final List<Definition> SHADERS = List.of(
            new Definition(SoftParticles.SHADER_ID, DefaultVertexFormat.PARTICLE, SoftParticles::onShaderLoaded, SoftParticles::onShaderFailed),
            new Definition(AuroraRenderer.SHADER_ID, DefaultVertexFormat.POSITION_TEX_COLOR, AuroraRenderer::onShaderLoaded, AuroraRenderer::onShaderFailed));

    private ModShaders() {
    }
}
