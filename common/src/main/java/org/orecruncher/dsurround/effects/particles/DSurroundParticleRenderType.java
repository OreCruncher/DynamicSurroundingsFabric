package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
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
                    RenderSystem.defaultBlendFunc();
                // The particle engine made its particle shader current just before this; replace it if we can
                SoftParticles.begin(softDistance);
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            public String toString() {
                return name;
            }
        };
    }
}
