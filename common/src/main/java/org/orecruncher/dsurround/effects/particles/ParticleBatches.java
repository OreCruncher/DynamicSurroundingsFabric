package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;

import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link ParticleBatchGuard} on the game's particle drawing, for Minecraft's own particle sheets, called from
 * {@code MixinParticleEngine}. Render thread only.
 */
public final class ParticleBatches {

    // Minecraft's sheets, whose render types set up all the state they draw with. Other mods' render types are left
    // alone: some may expect their particles to set state themselves.
    private static final Set<ParticleRenderType> GUARDED = Set.of(
            ParticleRenderType.TERRAIN_SHEET,
            ParticleRenderType.PARTICLE_SHEET_OPAQUE,
            ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
            ParticleRenderType.PARTICLE_SHEET_LIT);

    private static final ParticleBatchGuard GUARD = new ParticleBatchGuard(new OpenGl(),
            message -> MixinHelpers.LOGGER.warn("%s", message));

    private ParticleBatches() {
    }

    /**
     * A render type has begun its batch. Not guarded with the Repair Particle Batches guard off (Diagnostic Options,
     * Guards), which so turns off the repairs and drawing particles on their own as well, from the next batch.
     *
     * @param builder what it returned; null if it draws nothing this way
     */
    public static void begun(ParticleRenderType type, @Nullable BufferBuilder builder) {
        GUARD.begun(type.toString(), builder != null && GUARDED.contains(type)
                && MixinHelpers.guardsConfig.repairParticleBatches);
    }

    public static void rendered(Object particle) {
        GUARD.rendered(particle);
    }

    // For a particle drawn on its own: its vertices, in the format Minecraft's sheets use. Grows as needed, kept.
    private static final ByteBufferBuilder ISOLATED = new ByteBufferBuilder(16 * 1024);

    /**
     * Whether a particle is to be drawn on its own (see {@link ParticleBatchGuard#isolates})
     */
    public static boolean isolates(Object particle) {
        return GUARD.isolates(particle);
    }

    /**
     * Draws a particle on its own, with the state it sets as it renders, then puts the batch's state back
     *
     * @param render renders the particle into the consumer it is given
     */
    public static void isolated(Consumer<VertexConsumer> render) {
        try {
            var builder = new BufferBuilder(ISOLATED, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            render.accept(builder);
            var mesh = builder.build();
            if (mesh != null)
                BufferUploader.drawWithShader(mesh);
        } finally {
            GUARD.restore();
        }
    }

    public static void beforeDraw() {
        GUARD.beforeDraw();
    }

    /**
     * How many batches have had their state put back, for diagnostics
     */
    public static int repairs() {
        return GUARD.repairs();
    }

    private static final class OpenGl implements ParticleBatchGuard.Gl {

        @Override
        public ParticleBatchGuard.State read(boolean withBlend) {
            var shader = RenderSystem.getShader();
            int texture = RenderSystem.getShaderTexture(0);
            if (!withBlend)
                return new ParticleBatchGuard.State(shader, texture, false, 0, 0, 0, 0);
            // The GL's own state, not what Minecraft's state cache believes: a mod may set it straight through the GL
            return new ParticleBatchGuard.State(shader, texture,
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        }

        @Override
        public void restore(ParticleBatchGuard.State state) {
            var shader = (ShaderInstance) state.shader();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, state.texture());
            // Through Minecraft's state cache, so it agrees, then straight to the GL: if the change went past the
            // cache, the cache already holds these values and would skip them
            if (state.blend())
                RenderSystem.enableBlend();
            else
                RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(state.srcRgb(), state.dstRgb(), state.srcAlpha(), state.dstAlpha());
            if (state.blend())
                GL11.glEnable(GL11.GL_BLEND);
            else
                GL11.glDisable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(state.srcRgb(), state.dstRgb(), state.srcAlpha(), state.dstAlpha());
        }
    }
}
