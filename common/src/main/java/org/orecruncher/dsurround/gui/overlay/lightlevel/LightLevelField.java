package org.orecruncher.dsurround.gui.overlay.lightlevel;

import net.minecraft.world.entity.EntityTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The blocks around the player a monster could stand on, with their light, kept a section (16 cubed) at a time.
 * A section is looked at again only when something says it changed: a block in it or next to it (block updates),
 * its light, or its chunk loading. Moving only adds the sections coming into range. Light changes only re-read the
 * light of the blocks already found, which is cheap; anything else scans the section again, nearest first, a few
 * sections a tick, so a teleport or a large change is spread over a few ticks.
 * <p>
 * Everything here runs on the client thread: the scan reads blocks and asks the spawn rules, which mod blocks may
 * answer by looking at block entities.
 */
final class LightLevelField {

    /**
     * Full scans per tick. An empty section costs next to nothing; a full one up to 4096 block reads.
     */
    static final int SCANS_PER_TICK = 12;
    /**
     * Blocks above the player's feet that are looked at; the range applies below and around
     */
    static final int RANGE_UP = 3;

    static final class Section {
        final long key;
        final int x;
        final int y;
        final int z;
        final AABB bounds;
        int[] entries = new int[32];
        int count;
        boolean dirty = true;
        boolean lightDirty;

        Section(int x, int y, int z) {
            this.key = SectionPos.asLong(x, y, z);
            this.x = x;
            this.y = y;
            this.z = z;
            this.bounds = new AABB(x << 4, y << 4, z << 4, (x + 1) << 4, (y + 1) << 4, (z + 1) << 4);
        }

        void add(int entry) {
            if (this.count == this.entries.length) {
                var grown = new int[this.entries.length * 2];
                System.arraycopy(this.entries, 0, grown, 0, this.count);
                this.entries = grown;
            }
            this.entries[this.count++] = entry;
        }
    }

    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final List<Section> pending = new ArrayList<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

    // The sections in range, inclusive; an empty range (min above max) until the first update
    private int minX = 1, maxX = 0, minY = 1, maxY = 0, minZ = 1, maxZ = 0;
    private int scansLastTick;

    void clear() {
        this.sections.clear();
        this.pending.clear();
        this.minX = this.minY = this.minZ = 1;
        this.maxX = this.maxY = this.maxZ = 0;
        this.scansLastTick = 0;
    }

    int sectionCount() {
        return this.sections.size();
    }

    int scansLastTick() {
        return this.scansLastTick;
    }

    void forEachSection(Consumer<Section> action) {
        for (var section : this.sections.values())
            action.accept(section);
    }

    /**
     * A block changed: its section scans again, and those above and below it if it is at their edge (the block
     * under a spot decides whether something stands there; a monster needs the block over it clear)
     */
    void blockChanged(int x, int y, int z) {
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        this.markDirty(sx, sy, sz);
        if (((y + 1) >> 4) != sy)
            this.markDirty(sx, sy + 1, sz);
        if (((y - 1) >> 4) != sy)
            this.markDirty(sx, sy - 1, sz);
    }

    /**
     * A section's light changed: its blocks read their light again
     */
    void lightChanged(int sx, int sy, int sz) {
        var section = this.sections.get(SectionPos.asLong(sx, sy, sz));
        if (section != null)
            section.lightDirty = true;
    }

    /**
     * A chunk arrived: every section of it in range scans again
     */
    void chunkChanged(int cx, int cz) {
        if (cx < this.minX || cx > this.maxX || cz < this.minZ || cz > this.maxZ)
            return;
        for (int sy = this.minY; sy <= this.maxY; sy++)
            this.markDirty(cx, sy, cz);
    }

    boolean isDirty(int sx, int sy, int sz) {
        var section = this.sections.get(SectionPos.asLong(sx, sy, sz));
        return section != null && section.dirty;
    }

    private void markDirty(int sx, int sy, int sz) {
        var section = this.sections.get(SectionPos.asLong(sx, sy, sz));
        if (section != null)
            section.dirty = true;
    }

    /**
     * Brings the sections in range up to date for a player at a block position: drops those now out of range,
     * adds those come into it, and scans the nearest of those that need it.
     */
    void update(ClientLevel level, int px, int py, int pz, int range) {
        this.setRange(level.getMinSectionY(), level.getMaxSectionY(), px, py, pz, range);

        this.pending.clear();
        for (var section : this.sections.values()) {
            if (section.dirty)
                this.pending.add(section);
            else if (section.lightDirty)
                this.refreshLight(level, section);
        }

        int scans = Math.min(this.pending.size(), SCANS_PER_TICK);
        if (this.pending.size() > SCANS_PER_TICK) {
            int cx = px >> 4, cy = py >> 4, cz = pz >> 4;
            this.pending.sort(Comparator.comparingInt(s -> distanceSq(s, cx, cy, cz)));
        }
        for (int i = 0; i < scans; i++)
            this.scan(level, this.pending.get(i));
        this.pending.clear();
        this.scansLastTick = scans;
    }

    /**
     * Sets the sections in range for a player at a block position: those out of it are dropped, new ones are added
     * to be scanned. Nothing changes while the player stays within the same sections.
     */
    void setRange(int levelMinY, int levelMaxY, int px, int py, int pz, int range) {
        int minX = (px - range) >> 4, maxX = (px + range) >> 4;
        int minZ = (pz - range) >> 4, maxZ = (pz + range) >> 4;
        int minY = Math.max(levelMinY, (py - range) >> 4), maxY = Math.min(levelMaxY, (py + RANGE_UP) >> 4);
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
    }

    private static int distanceSq(Section s, int cx, int cy, int cz) {
        int dx = s.x - cx, dy = s.y - cy, dz = s.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void refreshLight(ClientLevel level, Section section) {
        section.lightDirty = false;
        int baseX = section.x << 4, baseY = section.y << 4, baseZ = section.z << 4;
        var engine = level.getLightEngine();
        for (int i = 0; i < section.count; i++) {
            int entry = section.entries[i];
            this.pos.set(baseX + LightLevels.localX(entry), baseY + LightLevels.localY(entry), baseZ + LightLevels.localZ(entry));
            section.entries[i] = LightLevels.withLight(entry,
                    engine.getLayerListener(LightLayer.BLOCK).getLightValue(this.pos),
                    engine.getLayerListener(LightLayer.SKY).getLightValue(this.pos));
        }
    }

    private void scan(ClientLevel level, Section section) {
        section.dirty = false;
        section.lightDirty = false;
        section.count = 0;

        // Not loaded yet: the chunk arriving marks it again
        var chunk = level.getChunkSource().getChunk(section.x, section.z, ChunkStatus.FULL, false);
        if (chunk == null)
            return;
        int index = chunk.getSectionIndexFromSectionY(section.y);
        if (index < 0 || index >= chunk.getSectionsCount())
            return;
        LevelChunkSection blocks = chunk.getSection(index);
        LevelChunkSection under = index > 0 ? chunk.getSection(index - 1) : null;

        // Nothing to stand on in an empty section but the top of the one below it
        boolean underEmpty = under == null || under.hasOnlyAir();
        if (blocks.hasOnlyAir() && underEmpty)
            return;
        int layers = blocks.hasOnlyAir() ? 1 : 16;

        int baseX = section.x << 4, baseY = section.y << 4, baseZ = section.z << 4;
        var engine = level.getLightEngine();
        var blockLight = engine.getLayerListener(LightLayer.BLOCK);
        var skyLight = engine.getLayerListener(LightLayer.SKY);

        for (int ly = 0; ly < layers; ly++) {
            if (ly == 0 && underEmpty)
                continue;
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    BlockState belowState = ly > 0 ? blocks.getBlockState(lx, ly - 1, lz) : under.getBlockState(lx, 15, lz);
                    if (belowState.isAir())
                        continue;
                    BlockState state = blocks.getBlockState(lx, ly, lz);
                    this.pos.set(baseX + lx, baseY + ly, baseZ + lz);
                    // Most of a section underground; the shape is cached for nearly every block
                    if (!state.isAir() && state.isCollisionShapeFullBlock(level, this.pos))
                        continue;
                    this.below.setWithOffset(this.pos, Direction.DOWN);
                    float height = labelHeight(level, state, belowState);
                    if (Float.isNaN(height))
                        continue;
                    boolean spawnable = SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, this.pos);
                    section.add(LightLevels.pack(lx, ly, lz, blockLight.getLightValue(this.pos), skyLight.getLightValue(this.pos), spawnable, height));
                }
        }
    }

    /**
     * Where the label goes over a spot, in blocks above its bottom: on whatever is under it (below 0 for a slab),
     * or on a thin block in it (carpet, snow). NaN where nothing can stand: in a fluid, in or on a full block's
     * worth of collision, on nothing.
     */
    private float labelHeight(ClientLevel level, BlockState state, BlockState belowState) {
        if (!state.getFluidState().isEmpty() || !belowState.getFluidState().isEmpty())
            return Float.NaN;
        var belowShape = belowState.getCollisionShape(level, this.below);
        if (belowShape.isEmpty())
            return Float.NaN;
        float height = (float) belowShape.max(Direction.Axis.Y) - 1F;

        if (state.is(Blocks.SNOW)) {
            // Thin snow has no collision, but the label should sit on it
            height = Math.max(height, state.getValue(SnowLayerBlock.LAYERS) / 8F);
        } else if (!state.isAir()) {
            var shape = state.getCollisionShape(level, this.pos);
            if (!shape.isEmpty()) {
                float top = (float) shape.max(Direction.Axis.Y);
                // Standing on it puts you in the block above, which is looked at in its turn
                if (top >= 1F)
                    return Float.NaN;
                height = Math.max(height, top);
            }
        }
        return height;
    }
}
