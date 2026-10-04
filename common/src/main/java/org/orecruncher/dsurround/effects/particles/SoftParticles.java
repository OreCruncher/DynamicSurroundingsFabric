package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * "Soft" particles: drawn with a shader that fades a particle out as it nears whatever is behind it, so where it
 * passes into terrain or the surface of water it blends away instead of ending in a hard line. The shader needs the
 * scene's depth, so a copy of it is made just before the particles are drawn.
 * <p>
 * The shader is registered by each platform (it has its own API for that) and handed over with
 * {@link #onShaderLoaded}. If it isn't available, because it failed to load or an Iris shader pack is in use (which
 * replaces the rendering, so a shader of ours wouldn't fit in), particles are drawn the vanilla way instead.
 * <p>
 * Everything here runs on the render thread.
 */
public final class SoftParticles {

    public static final ResourceLocation SHADER_ID = Constants.asId("soft_particle");

    @Nullable
    private static ShaderInstance shader;
    private static final Supplier<ShaderInstance> SHADER_SUPPLIER = () -> shader;

    // The copy of the scene's depth the shader reads; can't be the depth buffer being drawn to
    @Nullable
    private static TextureTarget depthCopy;

    private SoftParticles() {
    }

    /**
     * Called by the platform when the shader has been loaded (on each resource reload).
     */
    public static void onShaderLoaded(ShaderInstance loaded) {
        shader = loaded;
        Library.LOGGER.info("Loaded the %s shader", SHADER_ID);
    }

    /**
     * Called by the platform when the shader couldn't be loaded. Particles are then drawn the vanilla way, rather than
     * the error stopping the game from loading its resources.
     */
    public static void onShaderFailed(Throwable error) {
        shader = null;
        Library.LOGGER.error(error, "Unable to load the %s shader; particles will be drawn without it", SHADER_ID);
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
        var current = shader;
        if (current == null || IrisCompat.isShaderPackInUse())
            return false;

        // With Fabulous graphics particles are drawn to their own target, which holds a copy of the scene's depth
        var minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.levelRenderer.getParticlesTarget();
        if (target == null)
            target = minecraft.getMainRenderTarget();

        var copy = depthCopyFor(target);
        copy.copyDepthFrom(target);
        // Copying leaves no frame buffer bound
        target.bindWrite(false);

        current.setSampler("DepthSampler", copy.getDepthTextureId());
        // The shader is shared by every soft render type, so each sets its own fade distance
        current.safeGetUniform("SoftDistance").set(softDistance);
        RenderSystem.setShader(SHADER_SUPPLIER);
        return true;
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

    /**
     * Whether an Iris shader pack is in use (Iris, or Oculus, its NeoForge port). Asked through Iris's public API by
     * reflection, so there is no dependency on it. If the API can't be reached, assumes not.
     */
    private static final class IrisCompat {

        @Nullable
        private static final Object API;
        @Nullable
        private static final Method IS_SHADER_PACK_IN_USE;

        static {
            Object api = null;
            Method method = null;
            if (Platform.isModLoaded(Constants.IRIS) || Platform.isModLoaded(Constants.OCULUS)) {
                try {
                    var apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = apiClass.getMethod("getInstance").invoke(null);
                    method = apiClass.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError e) {
                    Library.LOGGER.warn("Unable to reach the Iris API; soft particles may not suit shader packs: %s", e);
                }
            }
            API = api;
            IS_SHADER_PACK_IN_USE = method;
        }

        static boolean isShaderPackInUse() {
            if (API == null || IS_SHADER_PACK_IN_USE == null)
                return false;
            try {
                return (boolean) IS_SHADER_PACK_IN_USE.invoke(API);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
