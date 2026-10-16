package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wind other effects lean with.
 */
public class SceneWindTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void stillAir() {
        SceneWind.clear();
    }

    @Test
    void beforeTheFirstTickTheAirIsStill() {
        SceneWind.clear();
        assertEquals(0F, SceneWind.x(10, 10));
        assertEquals(0F, SceneWind.z(10, 10));
    }

    @Test
    void theWindIsTheWindServicesWind() {
        // Due east, toward positive x
        var wind = new Wind();
        WindAccess.set(wind, 0F, 0.2F);
        SceneWind.update(wind, 0, 0);
        assertEquals(0.2F, SceneWind.x(5, 5), 1e-5F);
        assertEquals(0F, SceneWind.z(5, 5), 1e-5F);
    }

    @Test
    void theWindHasItsOwnDiagnosticsLine() {
        var wind = new Wind();
        WindAccess.set(wind, 0F, 0.2F);
        assertEquals("Wind: 0.20 toward E (90)", WindService.diagnosticText(wind));
    }

    @Test
    void theOpenSkyLetsTheWindIn() {
        assertEquals(1F, SceneWind.exposure(15));
        assertEquals(0F, SceneWind.exposure(8), "in a cave or a building");
        assertEquals(0F, SceneWind.exposure(0));
        var part = SceneWind.exposure(11);
        assertTrue(part > 0F && part < 1F);
    }

    @Test
    void outUnderTheSkyAllOfTheWind() {
        var world = new World();
        world.put(0, 0, 0, Blocks.GRASS_BLOCK);
        assertEquals(1F, SceneWind.shelter(world, new BlockPos(0, 1, 0)));
    }

    @Test
    void aGlassRoofKeepsTheWindOut() {
        // Glass lets all the sky light through, so the light alone would let it in
        var world = new World();
        world.room(Blocks.GLASS);
        assertEquals(0F, SceneWind.shelter(world, new BlockPos(0, 1, 0)));
    }

    @Test
    void treesLetSomeOfItThrough() {
        var world = new World();
        world.put(0, 5, 0, Blocks.OAK_LEAVES);
        world.put(0, 7, 0, Blocks.OAK_LEAVES);
        assertEquals(SceneWind.CANOPY_SHARE, SceneWind.shelter(world, new BlockPos(0, 1, 0)));
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                world.put(x, 3, z, Blocks.OAK_PLANKS);
        assertEquals(0F, SceneWind.shelter(world, new BlockPos(0, 1, 0)), "a roof under the trees");
    }

    @Test
    void underAnOverhangTheWindComesInFromTheSide() {
        // A roof over x <= 0, open sky beyond
        var world = new World();
        for (int x = -5; x <= 0; x++)
            for (int z = -5; z <= 5; z++)
                world.put(x, 3, z, Blocks.STONE);
        assertEquals(SceneWind.ROOF_SHARE, SceneWind.shelter(world, new BlockPos(0, 1, 0)), "at the edge");
        assertEquals(SceneWind.ROOF_SHARE / 2, SceneWind.shelter(world, new BlockPos(-1, 1, 0)), "a block in");
        assertEquals(0F, SceneWind.shelter(world, new BlockPos(-2, 1, 0)), "further in");
    }

    @Test
    void aWallKeepsTheSideWindOut() {
        var world = new World();
        world.room(Blocks.STONE);
        world.put(-2, 1, 0, Blocks.AIR);
        assertEquals(SceneWind.ROOF_SHARE / 2, SceneWind.shelter(world, new BlockPos(-1, 1, 0)), "by an open door");
        world.put(-2, 1, 0, Blocks.GLASS_PANE);
        assertEquals(0F, SceneWind.shelter(world, new BlockPos(-1, 1, 0)), "by a window");
    }

    /**
     * Blocks by position; the top of a column is above the highest that blocks motion, as the heightmap has it
     */
    private static final class World implements SceneWind.Columns {
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
                if (pos.getX() == x && pos.getZ() == z && entry.getValue().blocksMotion())
                    top = Math.max(top, pos.getY() + 1);
            }
            return top;
        }

        @Override
        public BlockState state(BlockPos pos) {
            return this.blocks.getOrDefault(pos.immutable(), Blocks.AIR.defaultBlockState());
        }

        // The game's tags aren't bound in tests
        @Override
        public boolean isLeaves(BlockState state) {
            return state.getBlock() instanceof LeavesBlock;
        }
    }

    @Test
    void somethingLightIsCarriedAtAboutHalfTheWind() {
        // As a particle moves: pushed, then slowed by its drag, tick after tick
        for (float friction : new float[]{0.92F, 0.96F}) {
            float speed = 0F;
            for (int t = 0; t < 500; t++)
                speed = (speed + SceneWind.push(0.2F, 1F, friction)) * friction;
            assertEquals(0.2F * SceneWind.CARRIED_SHARE, speed, 0.2F * 0.06F, "drag " + friction);
        }
        assertEquals(0F, SceneWind.push(0.2F, 0F, 0.92F), "sheltered: no push");
    }

    @Test
    void aDriftCatchesTheWindAndGoesNoFurther() {
        // Whatever the particle does with its own speed, the drift alone never outruns the wind's carrying speed
        var carried = 0.2F * SceneWind.CARRIED_SHARE;
        float drift = 0F;
        float last = 0F;
        for (int t = 0; t < 2000; t++) {
            drift = SceneWind.drift(drift, 0.2F, 1F);
            assertTrue(drift >= last && drift <= carried, "tick " + t + ": " + drift);
            last = drift;
        }
        assertEquals(carried, drift, 1e-5F);
    }

    @Test
    void aNewParticleCatchesTheWindAsTheModsSmokeDoes() {
        // The smoke's speed under push and drag, beside a drift, from still air: close all the way
        var friction = 0.93F;
        var carried = 0.2F * SceneWind.CARRIED_SHARE;
        float speed = 0F;
        float drift = 0F;
        for (int t = 1; t <= 60; t++) {
            speed = (speed + SceneWind.push(0.2F, 1F, friction)) * friction;
            drift = SceneWind.drift(drift, 0.2F, 1F);
            assertEquals(speed, drift, carried * 0.1F, "tick " + t);
        }
        assertTrue(SceneWind.drift(0F, 0.2F, 1F) < carried * 0.1F, "not at once");
    }

    @Test
    void aGustEasesInAndOut() {
        var drift = 0.1F * SceneWind.CARRIED_SHARE;
        var gusting = SceneWind.drift(drift, 0.2F, 1F);
        assertTrue(gusting > drift && gusting < 0.2F * SceneWind.CARRIED_SHARE * 0.6F, "a step, not a jump");
        var easing = SceneWind.drift(gusting, 0.1F, 1F);
        assertTrue(easing < gusting && easing > drift, "dies away");
    }

    @Test
    void outOfTheWindADriftDiesAway() {
        float drift = 0.1F;
        for (int t = 0; t < 200; t++)
            drift = SceneWind.drift(drift, 0.2F, 0F);
        assertEquals(0F, drift, 1e-5F);
        assertEquals(0F, SceneWind.drift(0F, 0F, 1F), "still air: none");
    }
}
