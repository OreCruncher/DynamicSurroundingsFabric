package org.orecruncher.dsurround.effects.clouds;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The clouds' quads, kept in sections of {@link #SECTION} by {@link #SECTION} cells, so that when a few cells change
 * only their sections are built again, not every quad: with a large grid, building them all was most of the clouds'
 * cost. Small sections, as in a storm cells change here and there all over the sky, and each takes a whole section. A cell that changes takes its own section, and the one beside it if it is on a section's edge, as its
 * neighbors' sides are drawn by what it is.
 * <p>
 * Positions are from an anchor cell in cloud space, not the grid's corner, so a section's quads stay right as the grid
 * moves with the camera; the anchor moves (and every section is built again) only once the camera is far from it, to
 * keep positions to a float's precision. A changing part's start time is from an epoch tick for the same reason.
 * <p>
 * After {@link #update}, {@link #settled} and {@link #changing} hold every section's vertices one after another, laid
 * out as {@code DefaultVertexFormat.POSITION_TEX_COLOR}: position, UV0 (a change's start and direction, see
 * {@link CloudMesh.Sink}), then color as bytes. Render thread only.
 */
public final class CloudSections {

    /**
     * A section's width, in cells
     */
    public static final int SECTION = 4;
    /**
     * A vertex's size, in bytes: three floats of position, two of UV0, four bytes of color
     */
    public static final int VERTEX_BYTES = 24;
    // How far, in cells, the grid's corner may get from the anchor before it moves; and the ticks from the epoch
    private static final int REANCHOR_CELLS = 1000;
    private static final long REEPOCH_TICKS = 1_000_000L;

    private static final class Section {
        ByteBuffer settled = allocate(4096);
        ByteBuffer changing = allocate(256);
        int settledVertices;
        int changingVertices;
    }

    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private boolean anchored;
    private int anchorX;
    private int anchorZ;
    private long epoch;
    // What the sections were built from: the field's generation and version, and where its grid was
    private int generation = Integer.MIN_VALUE;
    private int fieldVersion = Integer.MIN_VALUE;
    private int originX;
    private int originZ;
    private int size = -1;
    // Every section's vertices, one after another
    private ByteBuffer settled = allocate(64 * 1024);
    private ByteBuffer changing = allocate(4096);
    private int settledVertices;
    private int changingVertices;
    private int version;
    // How many sections were built in the last update, for tests and the diagnostics
    private int built;

    /**
     * Builds again the sections whose cells changed since the last update, and lays every section's vertices out
     * again; nothing if the field hasn't changed
     *
     * @return whether anything was built
     */
    public boolean update(CloudField field) {
        if (field.version() == this.fieldVersion && field.generation() == this.generation)
            return false;
        this.built = 0;
        int size = field.size();
        boolean all = field.generation() != this.generation || !this.anchored
                || Math.abs(field.originX() - this.anchorX) > REANCHOR_CELLS
                || Math.abs(field.originZ() - this.anchorZ) > REANCHOR_CELLS
                || field.tick() - this.epoch > REEPOCH_TICKS || field.tick() < this.epoch;
        if (all) {
            this.sections.clear();
            this.anchored = true;
            this.anchorX = field.originX() + size / 2;
            this.anchorZ = field.originZ() + size / 2;
            this.epoch = field.tick();
        }
        boolean moved = field.originX() != this.originX || field.originZ() != this.originZ || size != this.size;
        this.generation = field.generation();
        this.fieldVersion = field.version();
        this.originX = field.originX();
        this.originZ = field.originZ();
        this.size = size;

        // The sections the grid covers now, in cloud space
        int sx0 = Math.floorDiv(this.originX, SECTION), sx1 = Math.floorDiv(this.originX + size - 1, SECTION);
        int sz0 = Math.floorDiv(this.originZ, SECTION), sz1 = Math.floorDiv(this.originZ + size - 1, SECTION);
        if (moved)
            this.sections.long2ObjectEntrySet().removeIf(e -> {
                int sx = sectionX(e.getLongKey()), sz = sectionZ(e.getLongKey());
                return sx < sx0 || sx > sx1 || sz < sz0 || sz > sz1;
            });

        for (int sz = sz0; sz <= sz1; sz++)
            for (int sx = sx0; sx <= sx1; sx++) {
                long key = key(sx, sz);
                var section = this.sections.get(key);
                // A section with the grid's edge in it may have gained or lost cells as the grid moved, and its cells
                // on the edge sides to show where there were neighbors
                boolean edge = sx * SECTION <= this.originX || (sx + 1) * SECTION >= this.originX + size
                        || sz * SECTION <= this.originZ || (sz + 1) * SECTION >= this.originZ + size;
                if (section == null || all || (moved && edge) || this.isDirty(field, sx, sz)) {
                    if (section == null) {
                        section = new Section();
                        this.sections.put(key, section);
                    }
                    this.build(field, section, sx, sz);
                    this.built++;
                }
            }
        field.clearDirty();
        this.layOut();
        this.version++;
        return true;
    }

    /**
     * Whether any cell in a section, or on the edge of a section beside it, is dirty
     */
    private boolean isDirty(CloudField field, int sx, int sz) {
        int i0 = sx * SECTION - this.originX - 1, i1 = i0 + SECTION + 2;
        int j0 = sz * SECTION - this.originZ - 1, j1 = j0 + SECTION + 2;
        for (int j = j0; j < j1; j++)
            for (int i = i0; i < i1; i++)
                if (field.isDirty(i, j))
                    return true;
        return false;
    }

    private void build(CloudField field, Section section, int sx, int sz) {
        section.settled.clear();
        section.changing.clear();
        section.settledVertices = 0;
        section.changingVertices = 0;
        int i0 = sx * SECTION - this.originX, j0 = sz * SECTION - this.originZ;
        float start0 = (float) (field.tick() - this.epoch);
        CloudMesh.build(field, i0, j0, i0 + SECTION, j0 + SECTION, this.originX - this.anchorX, this.originZ - this.anchorZ,
                (x, y, z, shade, alpha, start, direction) -> {
                    section.settled = put(section.settled, x, y, z, start == 0F && direction == 0F ? 0F : start + start0, direction, shade, alpha);
                    section.settledVertices++;
                },
                (x, y, z, shade, alpha, start, direction) -> {
                    section.changing = put(section.changing, x, y, z, start + start0, direction, shade, alpha);
                    section.changingVertices++;
                });
    }

    private void layOut() {
        int settledVertices = 0, changingVertices = 0;
        for (var section : this.sections.values()) {
            settledVertices += section.settledVertices;
            changingVertices += section.changingVertices;
        }
        this.settled = room(this.settled, settledVertices * VERTEX_BYTES);
        this.changing = room(this.changing, changingVertices * VERTEX_BYTES);
        this.settled.clear();
        this.changing.clear();
        for (var section : this.sections.values()) {
            this.settled.put(section.settled.duplicate().flip());
            this.changing.put(section.changing.duplicate().flip());
        }
        this.settled.flip();
        this.changing.flip();
        this.settledVertices = settledVertices;
        this.changingVertices = changingVertices;
    }

    private static ByteBuffer put(ByteBuffer buffer, float x, float y, float z, float u, float v, float shade, float alpha) {
        if (buffer.remaining() < VERTEX_BYTES)
            buffer = grow(buffer, buffer.capacity() * 2);
        int gray = (int) (shade * 255F) & 0xFF;
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v)
                .put((byte) gray).put((byte) gray).put((byte) gray).put((byte) ((int) (alpha * 255F) & 0xFF));
        return buffer;
    }

    private static ByteBuffer room(ByteBuffer buffer, int bytes) {
        return buffer.capacity() >= bytes ? buffer : allocate(Math.max(bytes + bytes / 2, buffer.capacity() * 2));
    }

    private static ByteBuffer grow(ByteBuffer buffer, int capacity) {
        var bigger = allocate(capacity);
        bigger.put(buffer.flip());
        return bigger;
    }

    private static ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    private static long key(int sx, int sz) {
        return ((long) sx << 32) | (sz & 0xFFFFFFFFL);
    }

    private static int sectionX(long key) {
        return (int) (key >> 32);
    }

    private static int sectionZ(long key) {
        return (int) key;
    }

    /**
     * The settled part's vertices, read from its position to its limit; not to be kept past the next update
     */
    public ByteBuffer settled() {
        return this.settled;
    }

    public ByteBuffer changing() {
        return this.changing;
    }

    public int settledVertices() {
        return this.settledVertices;
    }

    public int changingVertices() {
        return this.changingVertices;
    }

    /**
     * Goes up each time the vertices are laid out again
     */
    public int version() {
        return this.version;
    }

    /**
     * The anchor cell positions are from, in cloud space, east-west; and {@link #anchorZ}
     */
    public int anchorX() {
        return this.anchorX;
    }

    public int anchorZ() {
        return this.anchorZ;
    }

    /**
     * The tick changes' start times are from
     */
    public long epoch() {
        return this.epoch;
    }

    /**
     * How many sections the last update built
     */
    public int built() {
        return this.built;
    }

    public int sections() {
        return this.sections.size();
    }

    /**
     * Forgets the sections, so the next update builds them all
     */
    public void clear() {
        this.sections.clear();
        this.anchored = false;
        this.generation = Integer.MIN_VALUE;
        this.fieldVersion = Integer.MIN_VALUE;
        this.settledVertices = 0;
        this.changingVertices = 0;
        this.settled.clear().flip();
        this.changing.clear().flip();
    }
}
