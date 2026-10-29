package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The {@code DynamicTransforms} uniform block for a draw: a model view matrix, with vanilla's other transforms left as
 * they are by default (white, no offset, no texture transform), as vanilla writes them for its own plain draws.
 */
public final class Transforms {

    /**
     * Whether clip space depth runs 0 to 1, rather than -1 to 1, which the shaders need to turn depth into distance:
     * it runs -1 to 1, as OpenGL has it, the only backend on this version
     */
    public static final boolean DEPTH_ZERO_TO_ONE = false;

    private static final Vector4f WHITE = new Vector4f(1F, 1F, 1F, 1F);
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f NO_TEXTURE_TRANSFORM = new Matrix4f();

    private Transforms() {
    }

    /**
     * Writes the transforms for this frame; render thread only
     */
    public static GpuBufferSlice of(Matrix4fc modelView) {
        return RenderSystem.getDynamicUniforms().writeTransform(modelView, WHITE, NO_OFFSET, NO_TEXTURE_TRANSFORM);
    }

    /**
     * The camera's: RenderSystem's model view matrix
     */
    public static GpuBufferSlice camera() {
        return of(RenderSystem.getModelViewMatrix());
    }
}
