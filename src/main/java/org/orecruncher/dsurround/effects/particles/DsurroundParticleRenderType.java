package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public class DsurroundParticleRenderType implements ParticleRenderType {

    private final ResourceLocation texture;

    public DsurroundParticleRenderType(final ResourceLocation texture) {
        this.texture = texture;
    }

    protected VertexFormat getVertexFormat() {
        return DefaultVertexFormat.PARTICLE; //.POSITION_TEXTURE_COLOR_LIGHT;
    }

    @Override
    public void begin(final BufferBuilder buffer, final TextureManager textureManager) {
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, this.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.Mode.QUADS, this.getVertexFormat());
    }

    @Override
    public void end(Tesselator tesselator) {
        tesselator.end();
    }

    protected ResourceLocation getTexture() {
        return this.texture;
    }

    @Override
    public String toString() {
        return this.texture.toString();
    }
}