package org.orecruncher.dsurround.processing.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.DimensionLibrary;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.world.WorldUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class CeilingScanner {

    private static final int SURVEY_INTERVAL = 4;
    private static final int INSIDE_SURVEY_RANGE = 3;
    private static final float INSIDE_THRESHOLD = 1.0F - 65.0F / 176.0F;
    private static final Cell[] cells;
    private static final float TOTAL_POINTS;
    private static final ObjectArray<Tag.Identified<Block>> NON_CEILING = new ObjectArray<>();

    static {

        final List<Cell> cellList = new ArrayList<>();
        // Build our cell map
        for (int x = -INSIDE_SURVEY_RANGE; x <= INSIDE_SURVEY_RANGE; x++)
            for (int z = -INSIDE_SURVEY_RANGE; z <= INSIDE_SURVEY_RANGE; z++)
                cellList.add(new Cell(new Vec3i(x, 0, z), INSIDE_SURVEY_RANGE));

        // Sort so the highest score cells are first
        Collections.sort(cellList);
        cells = cellList.toArray(new Cell[0]);

        float totalPoints = 0.0F;
        for (final Cell c : cellList)
            totalPoints += c.potentialPoints();
        TOTAL_POINTS = totalPoints;

        // Vanilla tags
        NON_CEILING.add(BlockTags.LEAVES);
        NON_CEILING.add(BlockTags.FENCE_GATES);
        NON_CEILING.add(BlockTags.FENCES);
        NON_CEILING.add(BlockTags.WALLS);
    }

    private boolean reallyInside = false;

    public void tick(long tickCount) {
        if (tickCount % SURVEY_INTERVAL != 0)
            return;

        final DimensionInfo dimInfo = DimensionLibrary.getData(GameUtils.getWorld());
        if (dimInfo.alwaysOutside()) {
            this.reallyInside = false;
        } else {
            final BlockPos pos = GameUtils.getPlayer().getBlockPos();
            float score = 0.0F;
            for (Cell cell : cells) score += cell.score(pos);
            float ceilingCoverageRatio = 1.0F - (score / TOTAL_POINTS);
            this.reallyInside = ceilingCoverageRatio > INSIDE_THRESHOLD;
        }
    }

    public boolean isReallyInside() {
        return this.reallyInside;
    }

    private static final class Cell implements Comparable<Cell> {

        private final Vec3i offset;
        private final float points;
        private final BlockPos.Mutable working;

        public Cell(final Vec3i offset, final int range) {
            this.offset = offset;
            final float xV = range - Math.abs(offset.getX()) + 1;
            final float zV = range - Math.abs(offset.getZ()) + 1;
            final float candidate = Math.min(xV, zV);
            this.points = candidate * candidate;
            this.working = new BlockPos.Mutable();
        }

        public float potentialPoints() {
            return this.points;
        }

        public float score(final BlockPos playerPos) {
            this.working.set(
                    playerPos.getX() + this.offset.getX(),
                    playerPos.getY() + this.offset.getY(),
                    playerPos.getZ() + this.offset.getZ()
            );

            final World world = GameUtils.getWorld();
            final int playerHeight = Math.max(playerPos.getY() + 1, 0);

            // Get the precipitation height
            this.working.setY(WorldUtils.getPrecipitationHeight(world, this.working));

            // Scan down looking for blocks that are considered "cover"
            while (this.working.getY() > playerHeight) {

                final BlockState state = world.getBlockState(this.working);

                if (actsAsCeiling(state)) {
                    // Cover block - no points for you!
                    return 0;
                }

                this.working.setY(this.working.getY() - 1);
            }

            // Scanned down to the players head and found nothing. So give the points.
            return this.points;
        }

        @Override
        public int compareTo(final Cell cell) {
            // Want big scores first in the list
            return -Float.compare(potentialPoints(), cell.potentialPoints());
        }

        @Override
        public String toString() {
            return this.offset.toString() + " points: " + this.points;
        }

        private boolean actsAsCeiling(final BlockState state) {
            // If it doesn't block movement it doesn't count as a ceiling.
            if (!state.getMaterial().blocksMovement())
                return false;

            // Test the block tags in our NON_CEILING set to see if any match
            final Block block = state.getBlock();
            for (final Tag.Identified<Block> tag : NON_CEILING) {
                if (tag.contains(block))
                    return false;
            }
            return true;
        }
    }

}