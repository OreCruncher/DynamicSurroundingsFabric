// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Draws one kind of mesh each frame (the weather's dust, drops and damp spots; the frost on windows; the aurora...),
 * written with a {@link QuadWriter}, in a render pass of its own, from a vertex buffer of its own that is kept from
 * frame to frame and grown as needed. With QuadWriter, the only place effects meet the game's buffer types. Each kind
 * has its own buffer so a later draw in the frame never writes over vertices an earlier one is still to read. Render
 * thread only.
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
     * Draws the quads written, laid out as {@code pipeline}'s vertex format has them. Positions are relative to the
     * camera; the fog and the other default uniforms are those set for the pass this is called from.
     *
     * @param bind binds what the pipeline needs besides the defaults: textures, and any uniforms of its own
     */
    public void draw(QuadWriter quads, RenderPipeline pipeline, RenderTarget target, Consumer<RenderPass> bind) {
        if (quads.vertexCount() > 0)
            this.draw(quads, pipeline, target, cameraTransforms(), bind);
    }

    /**
     * The same, with the transforms given: where RenderSystem's model view matrix isn't the camera's (while particles
     * are drawn, which use the transforms written as the frame was prepared), or the quads aren't placed from the
     * camera (the sky's)
     */
    public void draw(QuadWriter quads, RenderPipeline pipeline, RenderTarget target, GpuBufferSlice transforms, Consumer<RenderPass> bind) {
        int vertexCount = quads.vertexCount();
        if (vertexCount == 0)
            return;
        if (pipeline.getVertexFormatBinding(0) != quads.format().vertexFormat)
            throw new IllegalArgumentException(String.format("%s quads written as %s, which %s doesn't take", this.name, quads.format(), pipeline.getLocation()));
        this.draw(quads.vertices(), vertexCount / 4 * 6, pipeline, target, transforms, bind);
    }

    private static GpuBufferSlice cameraTransforms() {
        return RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
    }

    private void draw(ByteBuffer vertices, int indexCount, RenderPipeline pipeline, RenderTarget target,
                      GpuBufferSlice transforms, Consumer<RenderPass> bind) {
        var buffer = this.upload(vertices);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings " + this.name, target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
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
