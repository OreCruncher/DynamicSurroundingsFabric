package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DsurroundParticleRenderType implements ParticleRenderType {

    private final ResourceLocation texture;

    public DsurroundParticleRenderType(final ResourceLocation texture) {
        this.texture = texture;
    }

    protected VertexFormat getVertexFormat() {
        return DefaultVertexFormat.PARTICLE; //.POSITION_TEXTURE_COLOR_LIGHT;
    }

    @Override
    public BufferBuilder begin(final Tesselator buffer, final @NotNull TextureManager textureManager) {
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, this.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return buffer.begin(VertexFormat.Mode.QUADS, this.getVertexFormat());
    }

    protected ResourceLocation getTexture() {
        return this.texture;
    }

    @Override
    public String toString() {
        return this.texture.toString();
    }
}