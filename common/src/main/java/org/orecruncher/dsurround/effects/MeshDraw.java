package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Draws one kind of mesh each frame (the weather's dust, drops and damp spots; the frost on windows), in a render pass
 * of its own, from a vertex buffer of its own that is kept from frame to frame and grown as needed. Each kind has its
 * own buffer so a later draw in the frame never writes over vertices an earlier one is still to read. Render thread
 * only.
 */
public final class MeshDraw implements AutoCloseable {

    private final String name;
    @Nullable
    private GpuBuffer vertexBuffer;

    /**
     * @param name what is drawn, for the GPU's debugging labels
     */
    public MeshDraw(String name) {
        this.name = name;
    }

    /**
     * Draws a mesh, then closes it. Positions are relative to the camera; the fog and the other default uniforms are
     * those set for the pass this is called from.
     *
     * @param bind binds what the pipeline needs besides the defaults: textures, and any uniforms of its own
     */
    public void draw(MeshData mesh, RenderPipeline pipeline, RenderTarget target, Consumer<RenderPass> bind) {
        try (mesh) {
            this.draw(mesh.vertexBuffer(), mesh.drawState().indexCount(), mesh.drawState().primitiveTopology(), pipeline, target, cameraTransforms(), bind);
        }
    }

    /**
     * Draws quads already laid out in memory, as {@code pipeline}'s vertex format has them
     *
     * @param vertexCount how many corners there are: four a quad
     */
    public void drawQuads(ByteBuffer vertices, int vertexCount, RenderPipeline pipeline, RenderTarget target, Consumer<RenderPass> bind) {
        if (vertexCount > 0)
            this.draw(vertices, vertexCount / 4 * 6, PrimitiveTopology.QUADS, pipeline, target, cameraTransforms(), bind);
    }

    /**
     * The same, with the camera's transforms given: where RenderSystem's model view matrix isn't the camera's any more
     * (while particles are drawn, which use the transforms written as the frame was prepared)
     */
    public void drawQuads(ByteBuffer vertices, int vertexCount, RenderPipeline pipeline, RenderTarget target, GpuBufferSlice transforms, Consumer<RenderPass> bind) {
        if (vertexCount > 0)
            this.draw(vertices, vertexCount / 4 * 6, PrimitiveTopology.QUADS, pipeline, target, transforms, bind);
    }

    private static GpuBufferSlice cameraTransforms() {
        return RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
    }

    private void draw(ByteBuffer vertices, int indexCount, PrimitiveTopology topology, RenderPipeline pipeline, RenderTarget target,
                      GpuBufferSlice transforms, Consumer<RenderPass> bind) {
        var buffer = this.upload(vertices);
        var indices = RenderSystem.getSequentialBuffer(topology);
        var indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings " + this.name, target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            bind.accept(pass);
            pass.setVertexBuffer(0, buffer.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    private GpuBuffer upload(ByteBuffer vertices) {
        var device = RenderSystem.getDevice();
        if (this.vertexBuffer == null || this.vertexBuffer.size() < vertices.remaining()) {
            // Room to grow, so a storm building up doesn't make a new buffer every frame
            long size = vertices.remaining();
            if (this.vertexBuffer != null) {
                size = Math.max(size, this.vertexBuffer.size() * 3 / 2);
                this.vertexBuffer.close();
            }
            this.vertexBuffer = device.createBuffer(() -> "Dynamic Surroundings " + this.name + " vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
        }
        device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, vertices.remaining()), vertices);
        return this.vertexBuffer;
    }

    @Override
    public void close() {
        if (this.vertexBuffer != null) {
            this.vertexBuffer.close();
            this.vertexBuffer = null;
        }
    }
}
