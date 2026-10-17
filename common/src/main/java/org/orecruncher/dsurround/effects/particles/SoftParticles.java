package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

import java.lang.reflect.Method;

/**
 * "Soft" particles: drawn with a shader that fades a particle out as it nears whatever is behind it, so where it
 * passes into terrain or the surface of water it blends away instead of ending in a hard line. The shader needs the
 * scene's depth, so a copy of it is made just before the particles are drawn.
 * <p>
 * The shaders ({@link #SHADER}, and {@link #SCENE_DEPTH_SHADER} with Fabulous graphics) are registered by each
 * platform. If the one needed isn't available, because it failed to load or an Iris shader pack is in use (which
 * replaces the rendering, so a shader of ours wouldn't fit in), particles are drawn the vanilla way instead.
 * <p>
 * Everything here runs on the render thread.
 */
public final class SoftParticles {

    public static final ModShader SHADER = new ModShader("soft_particle", DefaultVertexFormat.PARTICLE,
            "soft particles", "particles will be drawn without it", Library.LOGGER);
    /**
     * The same, writing the scene's depth, for Fabulous graphics (see soft_particle_scene_depth.fsh). Apart from
     * {@link #SHADER} because a shader that writes depth can't have it tested before it runs.
     */
    public static final ModShader SCENE_DEPTH_SHADER = new ModShader("soft_particle_scene_depth",
            DefaultVertexFormat.PARTICLE, "soft particles", "particles will be drawn without it with Fabulous graphics",
            Library.LOGGER);

    // The copy of the scene's depth the shader reads; can't be the depth buffer being drawn to
    @Nullable
    private static TextureTarget depthCopy;

    private SoftParticles() {
    }

    /**
     * Sets up drawing with the soft particle shader, for a particle render type to call from its begin(): copies the
     * scene's depth and makes the shader current. Does nothing if the shader isn't available, leaving the vanilla
     * particle shader in place.
     * <p>
     * Each render type that calls this makes its own copy of the depth. Sharing one per frame would need to know
     * when a frame starts, and nothing reliable says so; the copy is a single blit, and the vanilla particle engine
     * only begins a render type that has particles to draw.
     *
     * @param softDistance how far in front of what is behind it, in blocks, a particle is at full strength
     * @return true if particles will be drawn soft
     */
    public static boolean begin(float softDistance) {
        var shader = Minecraft.useShaderTransparency() ? SCENE_DEPTH_SHADER : SHADER;
        var current = shader.get();
        if (current == null || !shader.isUsable())
            return false;

        // On a failure the vanilla particle shader is still current, so the particles are drawn the vanilla way
        return shader.run(() -> {
            bindSceneDepth(current);
            // The shader is shared by every soft render type, so each sets its own fade distance
            current.safeGetUniform("SoftDistance").set(softDistance);
            RenderSystem.setShader(shader.supplier());
        }, SoftParticles::bindParticleTarget);
    }

    /**
     * Copies the scene's depth as it is now, and gives the copy to {@code target} as its DepthSampler, for a shader
     * drawing particles that needs to know what is behind them.
     */
    public static void bindSceneDepth(ShaderInstance target) {
        bindSceneDepth(target, sceneDepthTarget(), particleTarget());
    }

    /**
     * Where the scene's depth is, water's surface included, for drawing that needs to know what is behind it: with
     * Fabulous graphics, the translucent target, which holds a copy of the scene's depth and the water drawn over it.
     * The particles' target has the scene's depth from before the water was drawn, and the weather's none at all.
     * Otherwise the main target, which has both.
     */
    public static RenderTarget sceneDepthTarget() {
        var minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.levelRenderer.getTranslucentTarget();
        return target != null ? target : minecraft.getMainRenderTarget();
    }

    /**
     * Copies the depth of {@code renderTarget} as it is now, and gives the copy to {@code target} as its DepthSampler,
     * for a shader that needs to know what is behind what it draws. Leaves {@code renderTarget} bound for drawing:
     * it should be the target being drawn to, such as the weather's own with Fabulous graphics.
     */
    public static void bindSceneDepth(ShaderInstance target, RenderTarget renderTarget) {
        bindSceneDepth(target, renderTarget, renderTarget);
    }

    /**
     * Copies the depth of {@code from} as it is now, and gives the copy to {@code target} as its DepthSampler; then
     * binds {@code drawTo} for drawing. For drawing to one target with another's depth: with Fabulous graphics, the
     * weather's target has no copy of the scene's depth (vanilla only clears it), so what is drawn there reads the
     * main target's.
     */
    public static void bindSceneDepth(ShaderInstance target, RenderTarget from, RenderTarget drawTo) {
        target.setSampler("DepthSampler", copySceneDepth(from, drawTo));
    }

    /**
     * Copies the depth of {@code from} as it is now, then binds {@code drawTo} for drawing; for drawing that reads the
     * copy in more than one shader.
     *
     * @return the copy's depth texture, which stays as it is until the next copy
     */
    public static int copySceneDepth(RenderTarget from, RenderTarget drawTo) {
        var copy = depthCopyFor(from);
        copy.copyDepthFrom(from);
        // Copying leaves no frame buffer bound
        drawTo.bindWrite(false);
        return copy.getDepthTextureId();
    }

    /**
     * Binds the render target particles are drawn to, for drawing. Puts things right after something drawing
     * particles failed part way through, possibly with another frame buffer bound.
     */
    public static void bindParticleTarget() {
        particleTarget().bindWrite(false);
    }

    /**
     * Where particles are drawn: with Fabulous graphics, a target of their own (which holds a copy of the scene's
     * depth, from before the water was drawn); otherwise the main target.
     */
    private static RenderTarget particleTarget() {
        var minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.levelRenderer.getParticlesTarget();
        return target != null ? target : minecraft.getMainRenderTarget();
    }

    /**
     * The depth copy, created or resized to match {@code target}. A depth copy is only possible between buffers of the
     * same format, so it has a stencil buffer if the target does (see {@link StencilCompat}).
     */
    private static TextureTarget depthCopyFor(RenderTarget target) {
        if (depthCopy == null) {
            depthCopy = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
        } else if (depthCopy.width != target.width || depthCopy.height != target.height) {
            depthCopy.resize(target.width, target.height, Minecraft.ON_OSX);
        }
        StencilCompat.match(target, depthCopy);
        return depthCopy;
    }

    /**
     * Render targets can have a stencil buffer on NeoForge, which adds isStencilEnabled() and enableStencil() to
     * RenderTarget; another mod may turn it on for the main target. Vanilla (and so Fabric) has no such methods, so
     * they are found by reflection, and this does nothing where they don't exist.
     */
    private static final class StencilCompat {

        @Nullable
        private static final Method IS_STENCIL_ENABLED = find("isStencilEnabled");
        @Nullable
        private static final Method ENABLE_STENCIL = find("enableStencil");

        @Nullable
        private static Method find(String name) {
            try {
                return RenderTarget.class.getMethod(name);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        /**
         * Gives {@code copy} a stencil buffer if {@code target} has one.
         */
        static void match(RenderTarget target, RenderTarget copy) {
            if (IS_STENCIL_ENABLED == null || ENABLE_STENCIL == null)
                return;
            try {
                if ((boolean) IS_STENCIL_ENABLED.invoke(target) && !(boolean) IS_STENCIL_ENABLED.invoke(copy))
                    ENABLE_STENCIL.invoke(copy);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Leave it as it is. If the formats then differ, the depth copy fails and the copy's contents are
                // undefined, which can fade the particles away entirely; but this only happens if reflection breaks.
            }
        }
    }
}
