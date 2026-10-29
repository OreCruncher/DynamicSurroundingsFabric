package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * A copy of a target's depth, for a shader to read what is behind what it draws: a pass can't read the depth buffer it
 * is drawing to. Made the first time, and again to match the source's size, or whether it has a stencil buffer (a depth
 * copy is only possible between textures of the same format). Render thread only.
 */
public final class DepthCopy {

    private final String label;
    @Nullable
    private TextureTarget copy;

    /**
     * @param label for the GPU's debugging labels
     */
    public DepthCopy(String label) {
        this.label = label;
    }

    /**
     * Copies {@code source}'s depth as it is now. Outside any render pass.
     */
    public void copyFrom(RenderTarget source) {
        if (this.copy != null && StencilCompat.hasStencil(this.copy) != StencilCompat.hasStencil(source)) {
            this.copy.destroyBuffers();
            this.copy = null;
        }
        if (this.copy == null)
            this.copy = StencilCompat.create(this.label, source.width, source.height, StencilCompat.hasStencil(source));
        else if (this.copy.width != source.width || this.copy.height != source.height)
            this.copy.resize(source.width, source.height);
        this.copy.copyDepthFrom(source);
    }

    /**
     * The copy, to bind; null before the first {@link #copyFrom}
     */
    @Nullable
    public GpuTextureView view() {
        return this.copy == null ? null : this.copy.getDepthTextureView();
    }

    /**
     * Render targets can have a stencil buffer on NeoForge, which adds a useStencil field to RenderTarget and a
     * TextureTarget constructor taking it; another mod may turn it on for the main target. Vanilla (and so Fabric)
     * has neither, so they are found by reflection, and targets are made without a stencil buffer where they don't
     * exist.
     */
    private static final class StencilCompat {

        @Nullable
        private static final Field USE_STENCIL = findField();
        @Nullable
        private static final Constructor<TextureTarget> WITH_STENCIL = findConstructor();

        @Nullable
        private static Field findField() {
            try {
                return RenderTarget.class.getField("useStencil");
            } catch (NoSuchFieldException e) {
                return null;
            }
        }

        @Nullable
        private static Constructor<TextureTarget> findConstructor() {
            try {
                return TextureTarget.class.getConstructor(String.class, int.class, int.class, boolean.class, boolean.class, GpuFormat.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        static boolean hasStencil(RenderTarget target) {
            if (USE_STENCIL == null)
                return false;
            try {
                return USE_STENCIL.getBoolean(target);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        static TextureTarget create(String label, int width, int height, boolean stencil) {
            if (stencil && WITH_STENCIL != null) {
                try {
                    return WITH_STENCIL.newInstance(label, width, height, true, true, GpuFormat.RGBA8_UNORM);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // Without a stencil buffer the formats differ and the depth copy fails, which turns the effects
                    // off; but this only happens if reflection breaks
                }
            }
            return new TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM);
        }
    }
}
