// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Records of floats on the GPU as texel buffers of four floats a texel, in batches of a fixed number of records, each
 * batch a buffer of its own: a GPU need only allow {@link #MAX_TEXELS} texels in a texel buffer, the game can't ask how
 * many more it allows, and it binds only a whole buffer as a texel buffer ("Uniform texel buffers do not support a slice
 * of a buffer"). Buffers are made as the records need them and kept. Render thread only.
 */
final class TexelBatches implements AutoCloseable {

    static final int MAX_TEXELS = 65_536;
    static final int FLOATS_A_TEXEL = 4;

    private final String name;
    private final int stride;
    private final int batchRecords;
    private final long batchBytes;
    private final List<GpuBuffer> buffers = new ArrayList<>();
    @Nullable
    private FloatBuffer staging;

    /**
     * @param name         what they hold, for the GPU's debugging labels
     * @param stride       floats a record, a whole number of texels
     * @param batchRecords records a batch, within {@link #MAX_TEXELS}
     */
    TexelBatches(String name, int stride, int batchRecords) {
        if (stride % FLOATS_A_TEXEL != 0)
            throw new IllegalArgumentException("a record of " + stride + " floats isn't a whole number of texels");
        if ((long) batchRecords * stride / FLOATS_A_TEXEL > MAX_TEXELS)
            throw new IllegalArgumentException("a batch of " + batchRecords + " records is more than " + MAX_TEXELS + " texels");
        this.name = name;
        this.stride = stride;
        this.batchRecords = batchRecords;
        this.batchBytes = (long) batchRecords * stride * Float.BYTES;
    }

    int batchRecords() {
        return this.batchRecords;
    }

    /**
     * Uploads {@code count} records from {@code data}, starting at record {@code first}
     */
    void upload(float[] data, int first, int count) {
        int floats = count * this.stride;
        if (this.staging == null || this.staging.capacity() < floats) {
            int capacity = Math.max(floats, 1);
            if (this.staging != null) {
                capacity = Math.max(capacity, this.staging.capacity() * 3 / 2);
                MemoryUtil.memFree(this.staging);
            }
            this.staging = MemoryUtil.memAllocFloat(capacity);
        }
        var buffer = this.staging.clear();
        buffer.put(data, first * this.stride, floats);
        buffer.flip();

        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var bytes = MemoryUtil.memByteBuffer(buffer);
        for (int batch = 0; (long) batch * this.batchBytes < bytes.remaining(); batch++) {
            if (batch == this.buffers.size()) {
                int number = batch + 1;
                this.buffers.add(device.createBuffer(() -> "Dynamic Surroundings " + this.name + " " + number,
                        GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, this.batchBytes));
            }
            int start = (int) (batch * this.batchBytes);
            int length = (int) Math.min(this.batchBytes, bytes.remaining() - start);
            encoder.writeToBuffer(this.buffers.get(batch).slice(0, length), bytes.slice(start, length));
        }
    }

    /**
     * The buffer of a batch, uploaded to
     */
    GpuBuffer batch(int batch) {
        return this.buffers.get(batch);
    }

    @Override
    public void close() {
        for (var buffer : this.buffers)
            buffer.close();
        this.buffers.clear();
        if (this.staging != null) {
            MemoryUtil.memFree(this.staging);
            this.staging = null;
        }
    }
}
