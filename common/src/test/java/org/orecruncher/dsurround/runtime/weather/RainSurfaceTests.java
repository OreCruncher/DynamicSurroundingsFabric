// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.tags.FluidTags;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.tags.BlockEffectTags;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the rain is taken to fall on where vanilla plays its rain sound, and the readout of the last ones.
 */
public class RainSurfaceTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // No block tags; the water and lava fluid tags hold vanilla's water and lava (tags aren't bound in tests)
    private static final ITagLibrary NO_TAGS = Fakes.of(ITagLibrary.class, Map.of("is", args -> args[1] instanceof FluidState fluid
            && ((args[0] == FluidTags.WATER && fluid.getType().isSame(Fluids.WATER))
            || (args[0] == FluidTags.LAVA && fluid.getType().isSame(Fluids.LAVA)))));
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    private static BlockGetter only(BlockState state) {
        return new BlockGetter() {
            @Override
            public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return pos.equals(POS) ? state : Blocks.AIR.defaultBlockState();
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
            public int getMinY() {
                return -64;
            }
        };
    }

    private static RainSurface at(BlockState state) {
        return RainSurface.of(only(state), POS, NO_TAGS);
    }

    private static RainSurface at(Block block) {
        return at(block.defaultBlockState());
    }

    @Test
    void waterAndWhatGrowsUnderItsSurfaceAreWater() {
        assertEquals(RainSurface.WATER, at(Blocks.WATER));
        assertEquals(RainSurface.WATER, at(Blocks.SEAGRASS));
        assertEquals(RainSurface.WATER, at(Blocks.KELP));
    }

    @Test
    void aWaterloggedSlabIsWhatItIsMadeOf() {
        var slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true);
        assertEquals(RainSurface.WOOD, at(slab));
    }

    @Test
    void lavaMagmaAndALitCampfireSizzle() {
        assertEquals(RainSurface.HOT, at(Blocks.LAVA));
        assertEquals(RainSurface.HOT, at(Blocks.MAGMA_BLOCK));
        assertEquals(RainSurface.HOT, at(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)));
        assertNotEquals(RainSurface.HOT, at(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false)));
    }

    @Test
    void theSoundTypeSortsBlocksWithNoTag() {
        assertEquals(RainSurface.METAL, at(Blocks.IRON_BLOCK));
        assertEquals(RainSurface.METAL, at(Blocks.COPPER_BLOCK.asList().getFirst()));
        assertEquals(RainSurface.METAL, at(Blocks.ANVIL));
        assertEquals(RainSurface.GLASS, at(Blocks.GLASS));
        assertEquals(RainSurface.GLASS, at(Blocks.ICE));
        assertEquals(RainSurface.WOOD, at(Blocks.OAK_PLANKS));
        assertEquals(RainSurface.WOOD, at(Blocks.SPRUCE_LOG));
        assertEquals(RainSurface.LEAVES, at(Blocks.CHERRY_LEAVES));
        assertEquals(RainSurface.SOFT, at(Blocks.GRASS_BLOCK));
        assertEquals(RainSurface.SOFT, at(Blocks.SAND));
        assertEquals(RainSurface.SOFT, at(Blocks.WOOL.pick(DyeColor.WHITE)));
        // Missed until the blocks dump showed them as hard ground
        assertEquals(RainSurface.WOOD, at(Blocks.CRIMSON_STEM));
        assertEquals(RainSurface.WOOD, at(Blocks.OAK_HANGING_SIGN));
        assertEquals(RainSurface.GLASS, at(Blocks.AMETHYST_CLUSTER));
        assertEquals(RainSurface.LEAVES, at(Blocks.AZALEA));
        assertEquals(RainSurface.SOFT, at(Blocks.WARPED_NYLIUM));
        assertEquals(RainSurface.SOFT, at(Blocks.SCULK));
        assertEquals(RainSurface.HARD, at(Blocks.RAW_IRON_BLOCK));
        assertEquals(RainSurface.HARD, at(Blocks.STONE));
        assertEquals(RainSurface.HARD, at(Blocks.BRICKS));
        assertEquals(RainSurface.HARD, at(Blocks.CONCRETE.pick(DyeColor.WHITE)));
    }

    @Test
    void aTagBeatsTheSoundType() {
        // Oak leaves sound like grass; the leaves tag makes them leaves. Rain absorbing grass is soft ground either way.
        var tags = Fakes.of(ITagLibrary.class, Map.of("is", args ->
                (args[0] == BlockEffectTags.RAIN_ON_LEAVES && args[1] instanceof BlockState s && s.is(Blocks.OAK_LEAVES))
                        || (args[0] == BlockEffectTags.RAIN_ON_METAL && args[1] instanceof BlockState c && c.is(Blocks.CAULDRON))));
        assertEquals(RainSurface.LEAVES, RainSurface.of(Blocks.OAK_LEAVES.defaultBlockState(), tags));
        assertEquals(RainSurface.METAL, RainSurface.of(Blocks.CAULDRON.defaultBlockState(), tags));
        assertEquals(RainSurface.SOFT, RainSurface.of(Blocks.OAK_LEAVES.defaultBlockState(), NO_TAGS));
    }

    @Test
    void theDumpSaysWhatSortedABlock() {
        var tags = Fakes.of(ITagLibrary.class, Map.of("is", args ->
                (args[0] == BlockEffectTags.RAIN_ON_METAL && args[1] instanceof BlockState c && c.is(Blocks.CAULDRON))
                        || (args[0] == FluidTags.WATER && args[1] instanceof FluidState f && f.is(Fluids.WATER))
                        || (args[0] == FluidTags.LAVA && args[1] instanceof FluidState l && l.is(Fluids.LAVA))));
        assertEquals("metal (tag " + BlockEffectTags.RAIN_ON_METAL.location() + ")",
                RainSurface.describe(Blocks.CAULDRON.defaultBlockState(), tags));
        assertEquals("metal (sound type IRON)", RainSurface.describe(Blocks.IRON_BLOCK.defaultBlockState(), tags));
        assertEquals("hard (no tag or sound type says otherwise)", RainSurface.describe(Blocks.RAW_IRON_BLOCK.defaultBlockState(), tags));
        assertTrue(RainSurface.describe(Blocks.WATER.defaultBlockState(), tags).startsWith("water"));
        assertTrue(RainSurface.describe(Blocks.MAGMA_BLOCK.defaultBlockState(), tags).startsWith("hot"));
        // The dump and the rain agree, the reason aside
        for (var block : new Block[]{Blocks.CAULDRON, Blocks.IRON_BLOCK, Blocks.GLASS, Blocks.OAK_PLANKS, Blocks.SAND, Blocks.STONE})
            assertTrue(RainSurface.describe(block.defaultBlockState(), tags)
                    .startsWith(RainSurface.of(block.defaultBlockState(), tags).name().toLowerCase(Locale.ROOT) + " ("));
        assertEquals("STONE", RainSurface.soundTypeName(Blocks.STONE.defaultBlockState().getSoundType()));
    }

    @Test
    void theTagsAreLookedAtInOrder() {
        // A block in more than one: metal first, soft ground last
        var tags = Fakes.of(ITagLibrary.class, Map.of("is", args -> true));
        assertEquals(RainSurface.METAL, RainSurface.of(Blocks.STONE.defaultBlockState(), tags));
        var softAndWood = Fakes.of(ITagLibrary.class, Map.of("is", args ->
                args[0] == BlockEffectTags.RAIN_ABSORBING || args[0] == BlockEffectTags.RAIN_ON_WOOD));
        assertEquals(RainSurface.WOOD, RainSurface.of(Blocks.STONE.defaultBlockState(), softAndWood));
    }

    @Test
    void theHardGroundLeavesTheSoundAsItWas() {
        for (boolean filtered : new boolean[]{false, true}) {
            assertEquals(1F, RainSurface.HARD.volume(filtered));
            assertEquals(1F, RainSurface.HARD.pitch(filtered));
        }
        assertNull(RainSurface.HARD.filter());
    }

    @Test
    void everyOtherSurfaceHasAFilterAndSensibleShares() {
        for (var surface : RainSurface.values()) {
            if (surface != RainSurface.HARD)
                assertNotNull(surface.filter(), surface::name);
            for (boolean filtered : new boolean[]{false, true}) {
                assertTrue(surface.volume(filtered) > 0F && surface.volume(filtered) <= 2F, surface::name);
                assertTrue(surface.pitch(filtered) >= 0.5F && surface.pitch(filtered) <= 2F, surface::name);
            }
        }
    }

    @Test
    void softGroundTakesTheTrebleAndMetalTheBass() {
        assertEquals(FixedFilter.Type.LOW_PASS, RainSurface.SOFT.filter().type());
        assertTrue(RainSurface.SOFT.filter().gainHF() < 1F);
        assertEquals(FixedFilter.Type.HIGH_PASS, RainSurface.METAL.filter().type());
        assertTrue(RainSurface.METAL.filter().gainLF() < 1F);
        // Glass is thinner than metal
        assertTrue(RainSurface.GLASS.filter().gainLF() < RainSurface.METAL.filter().gainLF());
    }

    @Test
    void aFiltersGainsAreZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> FixedFilter.lowPass(1.5F));
        assertThrows(IllegalArgumentException.class, () -> FixedFilter.highPass(-0.1F));
        assertThrows(IllegalArgumentException.class, () -> FixedFilter.lowPass(Float.NaN));
        var band = FixedFilter.bandPass(0.6F, 0.5F);
        assertEquals(0.6F, band.gainLF());
        assertEquals(0.5F, band.gainHF());
    }

    @Test
    void theRainSoundCarriesItsFilter() {
        var filter = FixedFilter.lowPass(0.25F);
        var sound = new RainSoundInstance(net.minecraft.sounds.SoundEvents.WEATHER_RAIN, net.minecraft.sounds.SoundSource.WEATHER,
                0.2F, 1F, net.minecraft.util.RandomSource.create(1L), 0.5, 64.5, 0.5, filter);
        assertSame(filter, sound.fixedFilter());
        assertEquals(net.minecraft.sounds.SoundSource.WEATHER, sound.getSource());
    }

    @Test
    void leavesMetalGlassAndWaterHaveRainOfTheirOwn() {
        assertEquals(StormSounds.RAIN_LEAVES, RainSurface.LEAVES.layer());
        assertEquals(StormSounds.RAIN_METAL, RainSurface.METAL.layer());
        assertEquals(StormSounds.RAIN_GLASS, RainSurface.GLASS.layer());
        assertEquals(StormSounds.RAIN_WATER, RainSurface.WATER.layer());
        assertNull(RainSurface.HARD.layer());
        assertNull(RainSurface.SOFT.layer());
        // In place of most of the rain, not on top of it: the layer and what the rain keeps add up to all of it
        for (var level : new float[]{0F, 0.3F, 0.6F, 1F}) {
            var all = StormSounds.calmShare(level) + StormSounds.mediumShare(level) + StormSounds.heavyShare(level);
            assertEquals(all, StormSounds.layerShare(level) + StormSounds.keptShare() * all, 1e-5F);
            assertTrue(StormSounds.layerShare(level) > StormSounds.keptShare() * all);
        }
        assertTrue(StormSounds.layerShare(1F) > StormSounds.layerShare(0F));
    }

    @Test
    void everySurfaceRainIsInSoundsJson() throws java.io.IOException {
        try (var in = RainSurfaceTests.class.getResourceAsStream("/assets/dsurround/sounds.json")) {
            assertNotNull(in);
            var json = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var surface : RainSurface.values())
                if (surface.layer() != null)
                    assertTrue(json.has(surface.layer().getPath()), () -> surface.layer() + " isn't in sounds.json");
        }
    }

    @Test
    void theTallyGivesSharesOfTheLastSoundsCommonestFirst() {
        var tally = new RainSurfaceTally();
        assertEquals("", tally.summary());
        for (int i = 0; i < 3; i++)
            tally.add(RainSurface.WATER);
        tally.add(RainSurface.LEAVES);
        assertEquals("water 75%, leaves 25%", tally.summary());
        // Only the last ones count
        for (int i = 0; i < RainSurfaceTally.SIZE; i++)
            tally.add(RainSurface.METAL);
        assertEquals("metal 100%", tally.summary());
    }
}
