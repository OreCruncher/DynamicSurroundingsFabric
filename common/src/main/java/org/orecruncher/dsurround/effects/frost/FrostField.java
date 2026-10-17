package org.orecruncher.dsurround.effects.frost;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The sides of windows around the player that frost ({@link FrostFaces}), kept a section (16 cubed) at a time, as the
 * light level overlay keeps its blocks. A section is looked at again when something in or near it changes (a block,
 * its light, its chunk loading), and in turn every few seconds, for the temperature, which a season changes. What
 * each face has frosted carries over a fresh look; a face new to the field starts with the frost it should have, so
 * windows come into range already frosted rather than frosting as the player arrives.
 * <p>
 * Client thread only: looking at a section reads blocks and their shapes.
 */
public final class FrostField {

    // Full looks at sections a tick. A section without frosting glass costs next to nothing.
    static final int SCANS_PER_TICK = 8;
    // How far around the player windows frost, across and up or down, in blocks
    public static final int RANGE = 32;
    static final int RANGE_VERTICAL = 16;
    // A block change this close to a section's edge can change the faces in the next (see FrostFaces.EDGE_REACH)
    static final int CHANGE_REACH = 3;

    static final class Section {
        final long key;
        final int x, y, z;
        List<FrostFace> faces = List.of();
        boolean dirty = true;

        Section(int x, int y, int z) {
            this.key = SectionPos.asLong(x, y, z);
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final List<Section> pending = new ArrayList<>();
    private final List<Section> inTurn = new ArrayList<>();
    private final Long2FloatOpenHashMap carried = new Long2FloatOpenHashMap();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int turn;
    private int faceCount;

    // The sections in range, inclusive; an empty range (min above max) until the first update
    private int minX = 1, maxX = 0, minY = 1, maxY = 0, minZ = 1, maxZ = 0;

    public void clear() {
        this.sections.clear();
        this.pending.clear();
        this.inTurn.clear();
        this.faceCount = 0;
        this.minX = this.minY = this.minZ = 1;
        this.maxX = this.maxY = this.maxZ = 0;
    }

    public int sectionCount() {
        return this.sections.size();
    }

    public int faceCount() {
        return this.faceCount;
    }

    public void forEachFace(Consumer<FrostFace> action) {
        // Called every frame: by index, as an iterator for each section's list was most of the mod's garbage
        for (var section : this.sections.values()) {
            var faces = section.faces;
            for (int i = 0, n = faces.size(); i < n; i++)
                action.accept(faces.get(i));
        }
    }

    /**
     * A block changed: the sections it could change faces in look again
     */
    public void blockChanged(int x, int y, int z) {
        for (int sx = (x - CHANGE_REACH) >> 4; sx <= (x + CHANGE_REACH) >> 4; sx++)
            for (int sy = (y - CHANGE_REACH) >> 4; sy <= (y + CHANGE_REACH) >> 4; sy++)
                for (int sz = (z - CHANGE_REACH) >> 4; sz <= (z + CHANGE_REACH) >> 4; sz++)
                    this.markDirty(sx, sy, sz);
    }

    /**
     * A section's light changed: a roof put up or taken down over a window changes which side is outside
     */
    public void lightChanged(int sx, int sy, int sz) {
        this.markDirty(sx, sy, sz);
    }

    /**
     * A chunk arrived: every section of it in range looks again
     */
    public void chunkChanged(int cx, int cz) {
        if (cx < this.minX || cx > this.maxX || cz < this.minZ || cz > this.maxZ)
            return;
        for (int sy = this.minY; sy <= this.maxY; sy++)
            this.markDirty(cx, sy, cz);
    }

    private void markDirty(int sx, int sy, int sz) {
        var section = this.sections.get(SectionPos.asLong(sx, sy, sz));
        if (section != null)
            section.dirty = true;
    }

    /**
     * Brings the sections in range up to date for a player at a block position, and the frost on every face a tick on
     *
     * @param target how much frost a face should have, worked out for it
     */
    public void update(ClientLevel level, IFrostWorld world, int px, int py, int pz, FrostTarget target) {
        this.setRange(level.getMinSectionY(), level.getMaxSectionY(), px, py, pz);

        // One section in turn looks again, for the temperature
        if (!this.inTurn.isEmpty()) {
            this.turn = (this.turn + 1) % this.inTurn.size();
            this.inTurn.get(this.turn).dirty = true;
        }

        this.pending.clear();
        for (var section : this.sections.values())
            if (section.dirty)
                this.pending.add(section);
        if (this.pending.size() > SCANS_PER_TICK) {
            int cx = px >> 4, cy = py >> 4, cz = pz >> 4;
            this.pending.sort(Comparator.comparingInt(s -> distanceSq(s, cx, cy, cz)));
        }
        int scans = Math.min(this.pending.size(), SCANS_PER_TICK);
        for (int i = 0; i < scans; i++)
            this.scan(level, world, this.pending.get(i), target);
        this.pending.clear();

        int count = 0;
        for (var section : this.sections.values()) {
            for (var face : section.faces) {
                face.target = target.of(face);
                face.shown = FrostConditions.ease(face.shown, face.target);
            }
            count += section.faces.size();
        }
        this.faceCount = count;
    }

    /**
     * Sets the sections in range for a player at a block position: those out of it are dropped, new ones are added to
     * be looked at. Nothing changes while the player stays within the same sections.
     */
    void setRange(int levelMinY, int levelMaxY, int px, int py, int pz) {
        int minX = (px - RANGE) >> 4, maxX = (px + RANGE) >> 4;
        int minZ = (pz - RANGE) >> 4, maxZ = (pz + RANGE) >> 4;
        int minY = Math.max(levelMinY, (py - RANGE_VERTICAL) >> 4), maxY = Math.min(levelMaxY, (py + RANGE_VERTICAL) >> 4);
        if (minX == this.minX && maxX == this.maxX && minY == this.minY && maxY == this.maxY && minZ == this.minZ && maxZ == this.maxZ)
            return;

        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;

        this.sections.values().removeIf(s -> s.x < minX || s.x > maxX || s.y < minY || s.y > maxY || s.z < minZ || s.z > maxZ);
        for (int sx = minX; sx <= maxX; sx++)
            for (int sy = minY; sy <= maxY; sy++)
                for (int sz = minZ; sz <= maxZ; sz++) {
                    long key = SectionPos.asLong(sx, sy, sz);
                    if (!this.sections.containsKey(key))
                        this.sections.put(key, new Section(sx, sy, sz));
                }
        this.inTurn.clear();
        this.inTurn.addAll(this.sections.values());
        this.turn = 0;
    }

    private static int distanceSq(Section s, int cx, int cy, int cz) {
        int dx = s.x - cx, dy = s.y - cy, dz = s.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void scan(ClientLevel level, IFrostWorld world, Section section, FrostTarget target) {
        section.dirty = false;
        var old = section.faces;
        section.faces = List.of();

        // Not loaded yet: the chunk arriving marks it again
        var chunk = level.getChunkSource().getChunk(section.x, section.z, ChunkStatus.FULL, false);
        if (chunk == null)
            return;
        int index = chunk.getSectionIndexFromSectionY(section.y);
        if (index < 0 || index >= chunk.getSectionsCount())
            return;
        var blocks = chunk.getSection(index);
        if (blocks.hasOnlyAir() || !blocks.maybeHas(world::frosts))
            return;

        this.carried.clear();
        for (var face : old)
            this.carried.put(face.key(), face.shown);

        var found = new ArrayList<FrostFace>();
        int baseX = section.x << 4, baseY = section.y << 4, baseZ = section.z << 4;
        for (int ly = 0; ly < 16; ly++)
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    var state = blocks.getBlockState(lx, ly, lz);
                    if (!world.frosts(state))
                        continue;
                    this.pos.set(baseX + lx, baseY + ly, baseZ + lz);
                    FrostFaces.facesOf(world, this.pos, state, face -> {
                        face.target = target.of(face);
                        face.shown = this.carried.containsKey(face.key()) ? this.carried.get(face.key()) : face.target;
                        found.add(face);
                    });
                }
        section.faces = found;
    }

    /**
     * How much frost a face should have, 0 to 1
     */
    @FunctionalInterface
    public interface FrostTarget {
        float of(FrostFace face);
    }
}
