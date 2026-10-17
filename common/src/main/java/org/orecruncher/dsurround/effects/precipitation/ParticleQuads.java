package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Quads in vanilla's particle vertex format ({@link DefaultVertexFormat#PARTICLE}: position, texture coordinates,
 * colour, light), written straight into memory, kept from frame to frame and grown as needed. Thousands of drops are
 * drawn a frame, four corners each; through a BufferBuilder that is four calls a corner, each finding where its part
 * of the vertex goes. Here a corner is written in one call, at offsets known in advance, and a quad's colour is packed
 * once rather than for each corner. The bytes are laid out as BufferBuilder lays them. Render thread only.
 */
public final class ParticleQuads implements AutoCloseable {

    static final int VERTEX_SIZE = 28;
    private static final int UV_OFFSET = 12;
    private static final int COLOR_OFFSET = 20;
    private static final int LIGHT_OFFSET = 24;
    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private ByteBuffer buffer;
    private long address;
    // How many vertices there is room for, and how many have been written
    private int capacity;
    private int vertices;

    public ParticleQuads() {
        if (DefaultVertexFormat.PARTICLE.getVertexSize() != VERTEX_SIZE)
            throw new IllegalStateException("The particle vertex format is no longer " + VERTEX_SIZE + " bytes");
    }

    /**
     * Starts over, with room for at least this many quads
     */
    public void begin(int quads) {
        int needed = Math.max(1, quads) * 4;
        if (this.buffer == null) {
            this.buffer = MemoryUtil.memAlloc(needed * VERTEX_SIZE);
            this.capacity = needed;
        } else if (this.capacity < needed) {
            int grown = Math.max(needed, this.capacity + this.capacity / 2);
            this.buffer = MemoryUtil.memRealloc(this.buffer, grown * VERTEX_SIZE);
            this.capacity = grown;
        }
        this.address = MemoryUtil.memAddress(this.buffer);
        this.vertices = 0;
    }

    /**
     * Writes a corner: where it is, its texture coordinates, its colour as {@link #color} packed it, and its packed
     * light. Grows to fit, keeping what is written, past what {@link #begin} made room for.
     */
    public void vertex(float x, float y, float z, float u, float v, int color, int light) {
        if (this.vertices == this.capacity) {
            int grown = Math.max(4, this.capacity + this.capacity / 2);
            this.buffer = MemoryUtil.memRealloc(this.buffer, grown * VERTEX_SIZE);
            this.address = MemoryUtil.memAddress(this.buffer);
            this.capacity = grown;
        }
        long p = this.address + (long) this.vertices * VERTEX_SIZE;
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        MemoryUtil.memPutFloat(p + UV_OFFSET, u);
        MemoryUtil.memPutFloat(p + UV_OFFSET + 4, v);
        MemoryUtil.memPutInt(p + COLOR_OFFSET, color);
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(p + LIGHT_OFFSET, light);
        } else {
            MemoryUtil.memPutShort(p + LIGHT_OFFSET, (short) (light & 0xFFFF));
            MemoryUtil.memPutShort(p + LIGHT_OFFSET + 2, (short) ((light >> 16) & 0xFFFF));
        }
        this.vertices++;
    }

    /**
     * A colour, 0 to 1 each, packed as the vertex holds it: red, green, blue and alpha bytes in that order, rounded as
     * BufferBuilder rounds them
     */
    public static int color(float red, float green, float blue, float alpha) {
        int abgr = ARGB.toABGR(ARGB.colorFromFloat(alpha, red, green, blue));
        return LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);
    }

    public int vertexCount() {
        return this.vertices;
    }

    /**
     * The vertices written since {@link #begin}, in a view of the memory that is good until the next begin
     */
    public ByteBuffer vertices() {
        return MemoryUtil.memByteBuffer(this.address, this.vertices * VERTEX_SIZE);
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
