package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ParticleRenderType implements ParticleTextureSheet {

    private final Identifier texture;

    public ParticleRenderType(final Identifier texture) {
        this.texture = texture;
    }

    protected VertexFormat getVertexFormat() {
        return VertexFormats.POSITION_TEXTURE_COLOR_LIGHT;
    }

    @Override
    public void begin(final BufferBuilder buffer, final TextureManager textureManager) {
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, this.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        buffer.begin(VertexFormat.DrawMode.QUADS, this.getVertexFormat());
    }

    protected Identifier getTexture() {
        return this.texture;
    }

    @Override
    public void draw(final Tessellator tessellator) {
        tessellator.draw();
    }

    @Override
    public String toString() {
        return this.texture.toString();
    }
}