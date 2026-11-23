// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

/**
 * A shader effect's uniform block, written each frame from its frame record (see AGENTS.md, "frame records"): the
 * inverse of the view and projection, then the record's vec4s in order, std140. Only vec4s follow the matrix, so
 * nothing is read out of place (a vec3 and a float after it are one vec4). A plain {@link GpuBuffer}, made at its first
 * write and kept while the device lives, not a ring buffer, whose fence held the GPU idle (see AGENTS.md).
 */
public final class FrameBlock {

    /**
     * What a frame record hands its block
     */
    public interface Frame {
        Matrix4fc invViewProj();

        float[] vec4s();
    }

    private final String label;
    private final int size;
    private @Nullable GpuBuffer buffer;

    /**
     * @param label   the buffer's name, for graphics debuggers
     * @param vectors how many vec4s follow the matrix
     */
    public FrameBlock(String label, int vectors) {
        this.label = label;
        var size = new Std140SizeCalculator().putMat4f();
        for (int i = 0; i < vectors; i++)
            size.putVec4();
        this.size = size.get();
    }

    /**
     * The block's size in bytes
     */
    public int size() {
        return this.size;
    }

    /**
     * Writes a frame's values into the block, making it first if need be
     *
     * @return the block, to bind
     */
    public GpuBuffer write(Frame frame) {
        var buffer = this.buffer;
        if (buffer == null)
            this.buffer = buffer = RenderSystem.getDevice().createBuffer(() -> this.label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, this.size);
        var v = frame.vec4s();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, this.size).putMat4f(frame.invViewProj());
            for (int i = 0; i < v.length; i += 4)
                builder.putVec4(v[i], v[i + 1], v[i + 2], v[i + 3]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get());
        }
        return buffer;
    }

    /**
     * The block as last written, or null before the first write
     */
    public @Nullable GpuBuffer buffer() {
        return this.buffer;
    }

    /**
     * Sets a vec4 of a frame record's values
     *
     * @param vector which vec4, from 0
     */
    public static void put(float[] v, int vector, float x, float y, float z, float w) {
        int i = vector * 4;
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = z;
        v[i + 3] = w;
    }
}
