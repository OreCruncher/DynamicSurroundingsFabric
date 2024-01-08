package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.world.WorldUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CeilingScanner {

    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private static final int SURVEY_INTERVAL = 4;
    private static final int INSIDE_SURVEY_RANGE = 3;
    private static final float INSIDE_THRESHOLD = 1.0F - 65.0F / 176.0F;
    private static final Cell[] cells;
    private static final float TOTAL_POINTS;
    private static final ObjectArray<TagKey<Block>> NON_CEILING = new ObjectArray<>();

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

    private final IDimensionLibrary dimensionLibrary;
    private boolean reallyInside = false;
    private float coverageRatio = 0;

    public CeilingScanner(IDimensionLibrary dimensionLibrary) {
        this.dimensionLibrary = dimensionLibrary;
    }

    public void tick(long tickCount) {
        if (tickCount % SURVEY_INTERVAL != 0)
            return;

        var world = GameUtils.getWorld().orElseThrow();
        final DimensionInfo dimInfo = this.dimensionLibrary.getData(world);
        if (dimInfo.alwaysOutside()) {
            this.reallyInside = false;
        } else {
            var player = GameUtils.getPlayer().orElseThrow();
            final BlockPos pos = player.blockPosition();
            float score = 0.0F;
            for (Cell cell : cells) score += cell.score(pos);
            this.coverageRatio = 1.0F - (score / TOTAL_POINTS);
            this.reallyInside = this.coverageRatio > INSIDE_THRESHOLD;
        }
    }

    public boolean isReallyInside() {
        return this.reallyInside;
    }

    public float getCoverageRatio() {
        return this.coverageRatio;
    }

    private static final class Cell implements Comparable<Cell> {

        private final Vec3i offset;
        private final float points;
        private final BlockPos.MutableBlockPos working;

        public Cell(final Vec3i offset, final int range) {
            this.offset = offset;
            final float xV = range - Math.abs(offset.getX()) + 1;
            final float zV = range - Math.abs(offset.getZ()) + 1;
            final float candidate = Math.min(xV, zV);
            this.points = candidate * candidate;
            this.working = new BlockPos.MutableBlockPos();
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

            final Level world = GameUtils.getWorld().orElseThrow();
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

            // Scanned down to the player head and found nothing. So give the points.
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
            // If it doesn't block movement, it doesn't count as a ceiling.
            if (!state.blocksMotion())
                return false;

            // Test the block tags in our NON_CEILING set to see if any match
            for (final TagKey<Block> tag : NON_CEILING) {
                if (TAG_LIBRARY.is(tag, state))
                    return false;
            }
            return true;
        }
    }
}