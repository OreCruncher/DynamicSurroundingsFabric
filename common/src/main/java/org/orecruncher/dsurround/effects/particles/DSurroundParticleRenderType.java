package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DSurroundParticleRenderType {

    public static final ParticleRenderType PARTICLE_SHEET_WATERFALL_CASCADE = new ParticleRenderType() {

        // TextureAtlas.LOCATION_PARTICLES is deprecated in 1.21.1, but vanilla's own particle sheets (opaque,
        // translucent and lit) use it and there is no replacement. This is vanilla's translucent sheet with depth
        // writes off. In 26.2 ParticleRenderType becomes a plain record and particle rendering is reworked, so this
        // class is rewritten for the port rather than updated.
        @SuppressWarnings("deprecation")
        public BufferBuilder begin(Tesselator tesselator, @NotNull TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_WATERFALL_CASCADE";
        }
    };
}
