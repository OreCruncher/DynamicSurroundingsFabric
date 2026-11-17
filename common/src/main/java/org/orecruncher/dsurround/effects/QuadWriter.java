// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Quads written straight into memory, in one of the vertex formats our pipelines take, kept from frame to frame and
 * grown as needed: what effects build their geometry with, so that only {@link MeshDraw} knows the game's buffer
 * types. Thousands of drops are drawn a frame, four corners each; through a BufferBuilder that is four calls a corner,
 * each finding where its part of the vertex goes. Here a corner is written in one call, at offsets known in advance,
 * and a quad's color can be packed once rather than for each corner. The bytes are laid out as BufferBuilder lays them.
 * One thread at a time: the falling drops' are written on a thread of their own and drawn on the render thread.
 */
public final class QuadWriter implements AutoCloseable {

    /**
     * The vertex formats written: each starts with position, texture coordinates and color, at the same offsets
     */
    public enum Format {
        /**
         * {@link DefaultVertexFormat#PARTICLE}: position, texture coordinates, color, light
         */
        PARTICLE(DefaultVertexFormat.PARTICLE, 28, true),
        /**
         * {@link DefaultVertexFormat#POSITION_TEX_COLOR}: position, texture coordinates, color
         */
        POSITION_TEX_COLOR(DefaultVertexFormat.POSITION_TEX_COLOR, 24, false);

        final VertexFormat vertexFormat;
        final int vertexSize;
        final boolean hasLight;

        Format(VertexFormat vertexFormat, int vertexSize, boolean hasLight) {
            this.vertexFormat = vertexFormat;
            this.vertexSize = vertexSize;
            this.hasLight = hasLight;
        }

        public int vertexSize() {
            return this.vertexSize;
        }
    }

    private static final int UV_OFFSET = 12;
    private static final int COLOR_OFFSET = 20;
    private static final int LIGHT_OFFSET = 24;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private final Format format;
    private final int vertexSize;
    private ByteBuffer buffer;
    private long address;
    // How many vertices there is room for, and how many have been written
    private int capacity;
    private int vertices;

    public QuadWriter(Format format) {
        if (format.vertexFormat.getVertexSize() != format.vertexSize)
            throw new IllegalStateException("The " + format + " vertex format is no longer " + format.vertexSize + " bytes");
        this.format = format;
        this.vertexSize = format.vertexSize;
    }

    public Format format() {
        return this.format;
    }

    /**
     * Starts over, with room for at least this many quads
     */
    public void begin(int quads) {
        int needed = Math.max(1, quads) * 4;
        if (this.buffer == null) {
            this.buffer = MemoryUtil.memAlloc(needed * this.vertexSize);
            this.capacity = needed;
        } else if (this.capacity < needed) {
            int grown = Math.max(needed, this.capacity + this.capacity / 2);
            this.buffer = MemoryUtil.memRealloc(this.buffer, grown * this.vertexSize);
            this.capacity = grown;
        }
        this.address = MemoryUtil.memAddress(this.buffer);
        this.vertices = 0;
    }

    /**
     * Writes a corner: where it is, its texture coordinates, its color as {@link #color} packed it, and its packed
     * light (left out by a format without light). Grows to fit, keeping what is written, past what {@link #begin} made
     * room for.
     */
    public void vertex(float x, float y, float z, float u, float v, int color, int light) {
        if (this.vertices == this.capacity) {
            int grown = Math.max(4, this.capacity + this.capacity / 2);
            this.buffer = MemoryUtil.memRealloc(this.buffer, grown * this.vertexSize);
            this.address = MemoryUtil.memAddress(this.buffer);
            this.capacity = grown;
        }
        long p = this.address + (long) this.vertices * this.vertexSize;
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        MemoryUtil.memPutFloat(p + UV_OFFSET, u);
        MemoryUtil.memPutFloat(p + UV_OFFSET + 4, v);
        MemoryUtil.memPutInt(p + COLOR_OFFSET, color);
        if (this.format.hasLight) {
            if (LITTLE_ENDIAN) {
                MemoryUtil.memPutInt(p + LIGHT_OFFSET, light);
            } else {
                MemoryUtil.memPutShort(p + LIGHT_OFFSET, (short) (light & 0xFFFF));
                MemoryUtil.memPutShort(p + LIGHT_OFFSET + 2, (short) ((light >> 16) & 0xFFFF));
            }
        }
        this.vertices++;
    }

    /**
     * Writes a corner of a format without light
     */
    public void vertex(float x, float y, float z, float u, float v, int color) {
        this.vertex(x, y, z, u, v, color, 0);
    }

    /**
     * A color, 0 to 1 each, packed as the vertex holds it: red, green, blue and alpha bytes in that order, rounded as
     * BufferBuilder rounds them
     */
    public static int color(float red, float green, float blue, float alpha) {
        int abgr = ARGB.toABGR(ARGB.colorFromFloat(alpha, red, green, blue));
        return LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);
    }

    public int vertexCount() {
        return this.vertices;
    }

    public int quadCount() {
        return this.vertices / 4;
    }

    /**
     * The vertices written since {@link #begin}, in a view of the memory that is good until the next begin
     */
    public ByteBuffer vertices() {
        return MemoryUtil.memByteBuffer(this.address, this.vertices * this.vertexSize);
    }

    @Override
    public void close() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
            this.buffer = null;
            this.capacity = 0;
            this.vertices = 0;
        }
    }
}
