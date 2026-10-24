package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how open to the weather a place is.
 */
public class ShelterTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The game's tags aren't bound in tests: the blocks Roofs leaves out, by their kind
    private static final Map<TagKey<Block>, Class<?>> KINDS = Map.of(
            BlockTags.LEAVES, LeavesBlock.class,
            BlockTags.FENCES, FenceBlock.class,
            BlockTags.FENCE_GATES, FenceGateBlock.class,
            BlockTags.WALLS, WallBlock.class);
    private static final ITagLibrary TAGS = Fakes.of(ITagLibrary.class, Map.of("is", args -> {
        var kind = KINDS.get(args[0]);
        return kind != null && args[1] instanceof BlockState state && kind.isInstance(state.getBlock());
    }));

    @Test
    void theOpenSkyLetsTheWeatherIn() {
        assertEquals(1F, Shelter.exposure(15));
        assertEquals(0F, Shelter.exposure(8), "in a cave or a building");
        assertEquals(0F, Shelter.exposure(0));
        var part = Shelter.exposure(11);
        assertTrue(part > 0F && part < 1F);
    }

    @Test
    void outUnderTheSkyAllOfIt() {
        var world = new World();
        world.put(0, 0, 0, Blocks.GRASS_BLOCK);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 1, 0)));
    }

    @Test
    void aGlassRoofKeepsTheWeatherOut() {
        // Glass lets all the sky light through, so the light alone would let it in
        var world = new World();
        world.room(Blocks.GLASS);
        assertEquals(0F, Shelter.roofShare(world, new BlockPos(0, 1, 0)));
    }

    @Test
    void treesAreNotARoof() {
        // The sky light under them, which each layer of leaves dims, says how much gets through
        var world = new World();
        world.put(0, 5, 0, Blocks.OAK_LEAVES);
        world.put(0, 7, 0, Blocks.OAK_LEAVES);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 1, 0)));
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                world.put(x, 3, z, Blocks.OAK_PLANKS);
        assertEquals(0F, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "a roof under the trees");
    }

    @Test
    void underAnOverhangItComesInFromTheSide() {
        // A roof over x <= 0, open sky beyond
        var world = new World();
        for (int x = -5; x <= 0; x++)
            for (int z = -5; z <= 5; z++)
                world.put(x, 3, z, Blocks.STONE);
        assertEquals(Shelter.ROOF_SHARE, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "at the edge");
        assertEquals(Shelter.ROOF_SHARE / 2, Shelter.roofShare(world, new BlockPos(-1, 1, 0)), "a block in");
        assertEquals(0F, Shelter.roofShare(world, new BlockPos(-2, 1, 0)), "further in");
    }

    @Test
    void aWallKeepsItOutFromTheSide() {
        var world = new World();
        world.room(Blocks.STONE);
        world.put(-2, 1, 0, Blocks.AIR);
        assertEquals(Shelter.ROOF_SHARE / 2, Shelter.roofShare(world, new BlockPos(-1, 1, 0)), "by an open door");
        world.put(-2, 1, 0, Blocks.GLASS_PANE);
        assertEquals(0F, Shelter.roofShare(world, new BlockPos(-1, 1, 0)), "by a window");
    }

    @Test
    void whatSomethingStartsInIsNotCover() {
        // Campfire smoke starts inside the campfire, mist inside the water
        var world = new World();
        world.put(0, 0, 0, Blocks.CAMPFIRE);
        world.put(1, 0, 0, Blocks.WATER);
        assertTrue(Blocks.CAMPFIRE.defaultBlockState().blocksMotion(), "the campfire is the top of its column");
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 0, 0)));
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(1, 0, 0)));
    }

    @Test
    void fallingWaterIsNotARoof() {
        var world = new World();
        for (int y = 1; y <= 10; y++)
            world.put(0, y, 0, Blocks.WATER);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 0, 0)));
    }

    @Test
    void aWaterSurfaceBesideAnOverhangIsOpenSky() {
        // The water tops its column, but nothing is over it
        var world = new World();
        for (int x = -5; x <= 0; x++)
            for (int z = -5; z <= 5; z++)
                world.put(x, 3, z, Blocks.STONE);
        world.put(1, 1, 0, Blocks.WATER);
        assertEquals(Shelter.ROOF_SHARE, Shelter.roofShare(world, new BlockPos(0, 1, 0)));
    }

    @Test
    void whatHangsOverheadIsNotARoof() {
        // Lanterns and chains block motion, but are too small to keep the weather off; nor does a fence rail or the
        // top of a wall
        var world = new World();
        world.put(0, 4, 0, Blocks.IRON_CHAIN);
        world.put(0, 3, 0, Blocks.LANTERN);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "a lantern on a chain");
        world.put(1, 3, 0, Blocks.OAK_FENCE);
        world.put(2, 3, 0, Blocks.COBBLESTONE_WALL);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(1, 1, 0)), "a fence");
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(2, 1, 0)), "a wall");
        world.put(3, 3, 0, Blocks.IRON_BARS);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(3, 1, 0)), "iron bars");
        world.put(4, 3, 0, Blocks.OAK_TRAPDOOR);
        assertEquals(Shelter.ROOF_SHARE, Shelter.roofShare(world, new BlockPos(4, 1, 0)), "a closed trapdoor is: open only at the side");
        world.put(0, 5, 0, Blocks.OAK_SLAB);
        assertEquals(Shelter.ROOF_SHARE, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "a slab above them is");
    }

    @Test
    void coverTooDeepToLookThroughIsARoof() {
        var world = new World();
        world.put(0, 1 + Shelter.MAX_COVER_DEPTH, 0, Blocks.OAK_LEAVES);
        assertEquals(1F, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "as deep as it looks");
        world.put(0, 1 + Shelter.MAX_COVER_DEPTH, 0, Blocks.AIR);
        world.put(0, 2 + Shelter.MAX_COVER_DEPTH, 0, Blocks.OAK_LEAVES);
        assertEquals(Shelter.ROOF_SHARE, Shelter.roofShare(world, new BlockPos(0, 1, 0)), "deeper: open only at the side");
    }

    /**
     * Blocks by position; the top of a column is above the highest that blocks motion or holds a fluid, as the
     * heightmap has it
     */
    private static final class World implements Shelter.Columns {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void put(int x, int y, int z, Block block) {
            this.blocks.put(new BlockPos(x, y, z), block.defaultBlockState());
        }

        // A 3x3 room around (0, 1, 0): walls of stone two high at +-2, roofed with the given block at y 3
        void room(Block roof) {
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    this.put(x, 3, z, roof);
                    if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                        this.put(x, 1, z, Blocks.STONE);
                        this.put(x, 2, z, Blocks.STONE);
                    }
                }
        }

        @Override
        public int top(int x, int z) {
            int top = 0;
            for (var entry : this.blocks.entrySet()) {
                var pos = entry.getKey();
                var state = entry.getValue();
                if (pos.getX() == x && pos.getZ() == z && (state.blocksMotion() || !state.getFluidState().isEmpty()))
                    top = Math.max(top, pos.getY() + 1);
            }
            return top;
        }

        @Override
        public BlockState state(BlockPos pos) {
            return this.blocks.getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean isRoof(BlockPos pos, BlockState state) {
            return Roofs.isRoof(EmptyBlockGetter.INSTANCE, pos, state, TAGS);
        }
    }
}
