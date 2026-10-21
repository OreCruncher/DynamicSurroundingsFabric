package org.orecruncher.dsurround.effects.frost;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which sides of a window frost, where their glass is, and how far the window reaches beyond them. A small
 * world of real blocks stands in for the level: a column sees the sky when nothing is above, and sky light is full
 * there unless set.
 */
public class FrostFacesTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    // A pane running east and west, its glass facing north and south
    private static final BlockState PANE_EAST_WEST = Blocks.GLASS_PANE.defaultBlockState()
            .setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);

    private static final class World implements IFrostWorld, BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Map<BlockPos, Integer> sky = new HashMap<>();

        World set(int x, int y, int z, BlockState state) {
            this.blocks.put(new BlockPos(x, y, z), state);
            return this;
        }

        World sky(int x, int y, int z, int light) {
            this.sky.put(new BlockPos(x, y, z), light);
            return this;
        }

        List<FrostFace> faces(int x, int y, int z) {
            var pos = new BlockPos(x, y, z);
            var found = new ArrayList<FrostFace>();
            FrostFaces.facesOf(this, pos, this.getBlockState(pos), found::add);
            return found;
        }

        @Override
        public BlockGetter blocks() {
            return this;
        }

        @Override
        public boolean frosts(BlockState state) {
            return state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE);
        }

        @Override
        public boolean seesSky(BlockPos pos) {
            for (var entry : this.blocks.entrySet()) {
                var at = entry.getKey();
                if (at.getX() == pos.getX() && at.getZ() == pos.getZ() && at.getY() > pos.getY() && !entry.getValue().isAir())
                    return false;
            }
            return true;
        }

        @Override
        public int skyLight(BlockPos pos) {
            return this.sky.getOrDefault(pos, 15);
        }

        @Override
        public int packedLight(BlockPos pos) {
            return 0xF000F0;
        }

        @Override
        public float temperature(BlockPos pos) {
            return 0F;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    /**
     * A glass block in a stone wall running north and south, a roofed room to its west and the open to its east
     */
    private static World houseWall() {
        return new World()
                .set(0, 65, -1, STONE).set(0, 65, 1, STONE).set(0, 66, 0, STONE).set(0, 64, 0, STONE)
                .set(0, 65, 0, GLASS)
                .set(-1, 67, 0, STONE)
                .sky(-1, 65, 0, 13);
    }

    @Test
    void aWindowInAHouseWallFrostsOutsideOnly() {
        var faces = houseWall().faces(0, 65, 0);
        assertEquals(1, faces.size());
        var face = faces.getFirst();
        assertEquals(Direction.EAST, face.side);
        assertEquals(1F + FrostFaces.LIFT, face.plane, 1.0E-5F);
        assertEquals(0F, face.u0, 1.0E-5F);
        assertEquals(1F, face.u1, 1.0E-5F);
        assertEquals(65F, face.v0, 1.0E-5F);
        assertEquals(66F, face.v1, 1.0E-5F);
    }

    @Test
    void underEavesTheOutsideStillFrosts() {
        // A porch roof over the outside too: more sky light reaches it than the room
        var world = houseWall().set(1, 67, 0, STONE).sky(1, 65, 0, 14);
        var faces = world.faces(0, 65, 0);
        assertEquals(1, faces.size());
        assertEquals(Direction.EAST, faces.getFirst().side);
    }

    @Test
    void underAGlassRoofTheInsideStaysClear() {
        // A greenhouse: its glass roof lets the sky light through, but is a roof
        var world = houseWall().set(-1, 67, 0, GLASS).sky(-1, 65, 0, 15);
        var faces = world.faces(0, 65, 0);
        assertEquals(1, faces.size());
        assertEquals(Direction.EAST, faces.getFirst().side);
    }

    @Test
    void glassBetweenTwoRoomsDoesNotFrost() {
        var world = houseWall().set(1, 67, 0, STONE).sky(1, 65, 0, 0).sky(-1, 65, 0, 0);
        assertTrue(world.faces(0, 65, 0).isEmpty());
    }

    @Test
    void aFreeStandingPaneFrostsOnBothSidesWhereItsGlassIs() {
        var faces = new World().set(0, 65, 0, PANE_EAST_WEST).faces(0, 65, 0);
        // North and south; its ends are too thin
        assertEquals(2, faces.size());
        for (var face : faces) {
            assertTrue(face.side == Direction.NORTH || face.side == Direction.SOUTH);
            float glass = face.side == Direction.NORTH ? 7F / 16F - FrostFaces.LIFT : 9F / 16F + FrostFaces.LIFT;
            assertEquals(glass, face.plane, 1.0E-5F);
            assertEquals(0F, face.u0, 1.0E-5F);
            assertEquals(1F, face.u1, 1.0E-5F);
        }
    }

    @Test
    void glassUnderWaterOrAgainstAWallDoesNotFrost() {
        var world = new World().set(0, 65, 0, GLASS)
                .set(1, 65, 0, STONE).set(-1, 65, 0, Blocks.WATER.defaultBlockState())
                .set(0, 65, 1, STONE).set(0, 65, -1, STONE);
        assertTrue(world.faces(0, 65, 0).isEmpty());
    }

    @Test
    void aWindowsEdgesAreFoundBeyondAFace() {
        // A row of four glass blocks along z, standing in the open
        var world = new World();
        for (int z = 0; z < 4; z++)
            world.set(0, 65, z, GLASS);
        var second = eastFace(world.faces(0, 65, 1));
        // One block to the window's north edge, two of the three to its south end, as far as is looked
        assertEquals(0F, second.edgeU0, 1.0E-5F);
        assertEquals(2F + FrostFaces.EDGE_REACH, second.edgeU1, 1.0E-5F);
        assertEquals(65F, second.edgeV0, 1.0E-5F);
        assertEquals(66F, second.edgeV1, 1.0E-5F);
        var first = eastFace(world.faces(0, 65, 0));
        assertEquals(0F, first.edgeU0, 1.0E-5F);
        assertEquals(3F, first.edgeU1, 1.0E-5F);
    }

    @Test
    void aWindowEndsWhereTheGlassIsNoLongerInLine() {
        // A pane set into the middle of its block beside a glass block: not the same plane, so the window ends there
        var world = new World().set(0, 65, 0, GLASS)
                .set(0, 65, 1, Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true));
        var face = eastFace(world.faces(0, 65, 0));
        assertEquals(1F, face.edgeU1, 1.0E-5F);
    }

    @Test
    void aWindowEndsWhereTheGlassIsCovered() {
        // Glass beside it with a wall in front: frost doesn't carry on behind the wall
        var world = new World().set(0, 65, 0, GLASS).set(0, 65, 1, GLASS).set(1, 65, 1, STONE);
        var face = eastFace(world.faces(0, 65, 0));
        assertEquals(1F, face.edgeU1, 1.0E-5F);
    }

    @Test
    void panesCarryAWindowOnUpAndDown() {
        var world = new World().set(0, 64, 0, PANE_EAST_WEST).set(0, 65, 0, PANE_EAST_WEST).set(0, 66, 0, PANE_EAST_WEST);
        var middle = world.faces(0, 65, 0).stream().filter(f -> f.side == Direction.NORTH).findFirst().orElseThrow();
        assertEquals(64F, middle.edgeV0, 1.0E-5F);
        assertEquals(67F, middle.edgeV1, 1.0E-5F);
    }

    private static FrostFace eastFace(List<FrostFace> faces) {
        return faces.stream().filter(f -> f.side == Direction.EAST).findFirst().orElseThrow();
    }
}
