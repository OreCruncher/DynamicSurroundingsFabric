package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for rain on windows: which side of a window the wind blows rain onto, and where a drop meets the glass of a
 * block or a pane. A small world of real blocks stands in for the level.
 */
public class RainOnWindowsTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class World implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        World set(int x, int y, int z, BlockState state) {
            this.blocks.put(new BlockPos(x, y, z), state);
            return this;
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

    @Test
    void theWindBlowsRainOntoTheSideFacingIt() {
        // Blowing east: onto west faces; blowing north (negative z): onto south faces
        assertEquals(Direction.WEST, Landings.windwardFace(1F, 0F, 1F, 0.5F, 0));
        assertEquals(Direction.EAST, Landings.windwardFace(-1F, 0F, 1F, 0.5F, 0));
        assertEquals(Direction.SOUTH, Landings.windwardFace(0F, -1F, 1F, 0.5F, 0));
        assertEquals(Direction.NORTH, Landings.windwardFace(0F, 1F, 1F, 0.5F, 0));
    }

    @Test
    void aSlantingWindReachesBothSidesInProportion() {
        // Mostly east, a little south: west faces most of the time, north faces the rest
        float x = 0.8F, z = 0.6F;
        int west = 0, north = 0;
        for (int i = 0; i < 100; i++) {
            var side = Landings.windwardFace(x, z, 1F, i / 100F, 0);
            if (side == Direction.WEST)
                west++;
            else if (side == Direction.NORTH)
                north++;
            else
                fail("rain blown onto the lee side: " + side);
        }
        assertEquals(57, west, 1);
        assertEquals(43, north, 1);
    }

    @Test
    void inStillAirAnySide() {
        for (int i = 0; i < 4; i++)
            assertTrue(Landings.windwardFace(1F, 0F, 0F, 0.5F, i).getAxis().isHorizontal());
        assertNotEquals(Landings.windwardFace(1F, 0F, 0F, 0.5F, 0), Landings.windwardFace(1F, 0F, 0F, 0.5F, 2));
    }

    @Test
    void aDropMeetsAGlassBlockOnItsFace() {
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState());
        var hit = Landings.trace(world, new Vec3(-2.5, 70.6, 0.5), 0.3F, -0.05F, 0F);
        assertNotNull(hit);
        assertEquals(Direction.WEST, hit.getDirection());
        assertEquals(new BlockPos(0, 70, 0), hit.getBlockPos());
        assertEquals(0D, hit.getLocation().x, 1e-6);
    }

    @Test
    void aDropMeetsAPaneWhereItsGlassIs() {
        // A pane on its own stands in the middle of its block: a drop meets it there, not at the block's edge
        var world = new World().set(0, 70, 0, Blocks.GLASS_PANE.defaultBlockState());
        var hit = Landings.trace(world, new Vec3(-2.5, 70.6, 0.5), 0.3F, -0.05F, 0F);
        assertNotNull(hit);
        assertEquals(Direction.WEST, hit.getDirection());
        assertTrue(hit.getLocation().x > 0.3D && hit.getLocation().x < 0.5D, () -> "met at " + hit.getLocation().x);
    }

    private static boolean isGlass(BlockState state) {
        return state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE);
    }

    @Test
    void aDropRunsDownGlassWhoseFaceCarriesOn() {
        // Three blocks of glass, one above the other: from the top one, two more below
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState())
                .set(0, 69, 0, Blocks.GLASS.defaultBlockState())
                .set(0, 68, 0, Blocks.GLASS.defaultBlockState());
        var at = new Vec3(0, 70.5, 0.5);
        assertEquals(2, Landings.glassBelow(world, new BlockPos(0, 70, 0), Direction.WEST, at, RainOnWindowsTests::isGlass));
    }

    @Test
    void notOntoAPaneSetBackBelowIt() {
        // Regression: tagged as a window, a pane under a glass block counted as more of it, though its glass is in the
        // middle of its block, and a drop ran on down the open air in front of it
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState())
                .set(0, 69, 0, Blocks.GLASS_PANE.defaultBlockState());
        assertEquals(0, Landings.glassBelow(world, new BlockPos(0, 70, 0), Direction.WEST, new Vec3(0, 70.5, 0.5), RainOnWindowsTests::isGlass));
    }

    @Test
    void notBehindSomethingInFrontOfIt() {
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState())
                .set(0, 69, 0, Blocks.GLASS.defaultBlockState())
                .set(-1, 69, 0, Blocks.STONE.defaultBlockState());
        assertEquals(0, Landings.glassBelow(world, new BlockPos(0, 70, 0), Direction.WEST, new Vec3(0, 70.5, 0.5), RainOnWindowsTests::isGlass));
    }

    @Test
    void notOntoAnythingElse() {
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState())
                .set(0, 69, 0, Blocks.STONE.defaultBlockState());
        assertEquals(0, Landings.glassBelow(world, new BlockPos(0, 70, 0), Direction.WEST, new Vec3(0, 70.5, 0.5), RainOnWindowsTests::isGlass));
    }

    @Test
    void itDripsOffAnEdgeOverOpenAir() {
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState());
        assertTrue(Landings.dripsOff(world, new Vec3(0, 70.5, 0.5), Direction.WEST, 70));
        // Ground far below doesn't stop it
        world.set(0, 60, 0, Blocks.STONE.defaultBlockState()).set(-1, 60, 0, Blocks.STONE.defaultBlockState());
        assertTrue(Landings.dripsOff(world, new Vec3(0, 70.5, 0.5), Direction.WEST, 70));
    }

    @Test
    void itRunsOntoASillOrTheWallBelow() {
        // The wall below, flush with the glass: the drop runs on down it
        var wall = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState()).set(0, 69, 0, Blocks.STONE.defaultBlockState());
        assertFalse(Landings.dripsOff(wall, new Vec3(0, 70.5, 0.5), Direction.WEST, 70));
        // A sill standing out under it, a slab's top a little lower
        var sill = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState()).set(-1, 69, 0, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        assertFalse(Landings.dripsOff(sill, new Vec3(0, 70.5, 0.5), Direction.WEST, 70));
    }

    @Test
    void itDripsOffInFrontOfSomethingSetBack() {
        // A pane under a glass block: its glass is in the middle of its block, behind the edge the drop runs off
        var world = new World().set(0, 70, 0, Blocks.GLASS.defaultBlockState()).set(0, 69, 0, Blocks.GLASS_PANE.defaultBlockState());
        assertTrue(Landings.dripsOff(world, new Vec3(0, 70.5, 0.5), Direction.WEST, 70));
    }

    @Test
    void underAPaneItDripsWhereThePaneIs() {
        // A pane over a full block: the block's side is flush with the pane's block, well in front of the pane's glass,
        // so the drop runs onto its top
        var world = new World().set(0, 70, 0, Blocks.GLASS_PANE.defaultBlockState()).set(0, 69, 0, Blocks.STONE.defaultBlockState());
        assertFalse(Landings.dripsOff(world, new Vec3(0.4375, 70.5, 0.5), Direction.WEST, 70));
        var hanging = new World().set(0, 70, 0, Blocks.GLASS_PANE.defaultBlockState());
        assertTrue(Landings.dripsOff(hanging, new Vec3(0.4375, 70.5, 0.5), Direction.WEST, 70));
    }

    @Test
    void glassIsTaggedForStreaks() throws Exception {
        try (var stream = RainOnWindowsTests.class.getResourceAsStream("/assets/dsurround/dsconfigs/tags/block/effects/rain_streaks.json")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var values = json.getAsJsonArray("values").toString();
            assertTrue(values.contains("#c:glass_blocks"));
            assertTrue(values.contains("#c:glass_panes"));
        }
    }
}
