package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.NotNull;

public class DSurroundParticleRenderType {

    /**
     * Puffs of mist and steam (see {@link SoftPuffParticle}): small, so a short fade, or a puff near the water would be
     * mostly faded out.
     */
    public static final ParticleRenderType PARTICLE_SHEET_MIST = softSheet("PARTICLE_SHEET_MIST", 0.4F, false, false);

    /**
     * Tongues of flame (see {@link FlameTongue}): soft, and adding their light to what is behind them, so overlapping
     * flames burn brighter. Adding changes the blend, which a render type can't put back when it is done; the particle
     * engine's mixin puts the usual blend back after all the particles are drawn. Before the flames, it draws the
     * light they cast (see {@link PointLights}).
     */
    public static final ParticleRenderType PARTICLE_SHEET_FLAME = softSheet("PARTICLE_SHEET_FLAME", 0.3F, true, true);

    /**
     * Fireflies, with their halos. Vanilla's translucent sheet with depth writes off; before the fireflies, it draws
     * the lights they cast (see {@link PointLights}).
     */
    public static final ParticleRenderType PARTICLE_SHEET_FIREFLY = new ParticleRenderType() {

        @SuppressWarnings("deprecation")
        public BufferBuilder begin(Tesselator tesselator, @NotNull TextureManager textureManager) {
            PointLights.draw(tesselator);
            // With Fabulous graphics their depth is written: the transparency pass sorts the particles' target by its
            // depth, which otherwise is the ground's under any water, so the water is laid over them and they look
            // dark there. They are small, and the vanilla shader drops their clear edges, so none cuts into another.
            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            translucentBlend();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        public String toString() {
            return "PARTICLE_SHEET_FIREFLY";
        }
    };

    /**
     * A sheet of particles drawn soft (see {@link SoftParticles}): fading out over {@code softDistance} blocks as they
     * near whatever is behind them. Each sheet is its own render type so it can have its own fade distance.
     * <p>
     * TextureAtlas.LOCATION_PARTICLES is deprecated in 1.21.1, but vanilla's own particle sheets (opaque, translucent
     * and lit) use it and there is no replacement. This is vanilla's translucent sheet with depth writes off, drawn
     * with the soft particle shader when it is available. In 26.2 ParticleRenderType becomes a plain record and
     * particle rendering is reworked, so this class (and SoftParticles) is rewritten for the port rather than updated.
     */
    /**
     * The usual blend, except with Fabulous graphics: there the particles are drawn to a target of their own, which the
     * transparency pass lays over the scene taking its colours as already multiplied by its alpha. The usual blend
     * replaces the alpha with each particle's own while adding up their colours, so where faint particles pile up (mist,
     * steam) the colour outgrows the alpha and comes out far too bright. Each particle's alpha is combined with what is
     * under it instead, as its colour is.
     */
    static void translucentBlend() {
        if (Minecraft.useShaderTransparency())
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        else
            RenderSystem.defaultBlendFunc();
    }

    private static ParticleRenderType softSheet(String name, float softDistance, boolean additive, boolean drawsLights) {
        return new ParticleRenderType() {

            @SuppressWarnings("deprecation")
            public BufferBuilder begin(Tesselator tesselator, @NotNull TextureManager textureManager) {
                if (drawsLights)
                    PointLights.draw(tesselator);
                RenderSystem.depthMask(false);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                RenderSystem.enableBlend();
                if (additive)
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                else
                    translucentBlend();
                // The particle engine made its particle shader current just before this; replace it if we can.
                // With Fabulous graphics depth is written, but only by the soft shader, which writes the scene's, not
                // the particle's: there it sorts the particles in front of water (see soft_particle.fsh). The vanilla
                // shader, if it couldn't be replaced, would write the particles' own, and they would cut into each
                // other.
                if (SoftParticles.begin(softDistance) && Minecraft.useShaderTransparency())
                    RenderSystem.depthMask(true);
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            public String toString() {
                return name;
            }
        };
    }
}
