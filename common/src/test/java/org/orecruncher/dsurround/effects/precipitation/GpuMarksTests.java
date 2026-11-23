// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for what the drops leave made on the GPU ({@link GpuMarks}, {@link PackedMarks}): records hold what the arrays
 * say of each mark, positions relative to the anchor, packed again only once said to have changed; batches stay within
 * what every GPU allows; and the quads' thread packs everything when the GPU makes it.
 */
public class GpuMarksTests {

    private static final double CAMERA_X = 1000.25, CAMERA_Y = 70.5, CAMERA_Z = -2000.75;

    private static String shader() throws IOException {
        return ShaderSources.read("core/precipitation_marks.vsh");
    }

    @Test
    void aRecordIsThreeTexelsAsTheShaderReadsIt() throws IOException {
        assertEquals(3 * TexelBatches.FLOATS_A_TEXEL, PackedMarks.STRIDE);
        assertTrue(shader().contains("int at = mark * 3;"));
    }

    @Test
    void aBatchFitsTheTexelsEveryGpuAllows() {
        assertDoesNotThrow(() -> new TexelBatches("marks", PackedMarks.STRIDE, GpuMarks.BATCH_MARKS).close());
    }

    @Test
    void theWidestRangeFitsTheSpotsInFewBatches() {
        // The most damp spots there can be (PrecipitationHandler.MAX_DAMP_SPOTS at the widest range) need no more than two
        int widest = PrecipitationField.forArea(2048, 32);
        assertTrue(widest <= 2 * GpuMarks.BATCH_MARKS, widest + " spots");
    }

    @Test
    void aFlecksRecordHoldsItsArrays() {
        var flecks = new SnowFlecks(10);
        flecks.add(CAMERA_X + 3.5, 64.0, CAMERA_Z - 1.25, SnowFlecks.FACE_WEST, 0.75F, 0.4F, 0x00F000A0, Randomizer.create(3));
        var packed = flecks.packed(CAMERA_X, CAMERA_Y, CAMERA_Z);
        assertEquals(1, packed.count);
        float[] r = packed.data;
        assertEquals(flecks.x[0] - packed.anchorX, r[0], 1e-4);
        assertEquals(flecks.y[0] - packed.anchorY, r[1], 1e-4);
        assertEquals(flecks.z[0] - packed.anchorZ, r[2], 1e-4);
        assertEquals(SnowFlecks.FACE_WEST, r[3]);
        assertEquals(flecks.age[0], r[4]);
        assertEquals(flecks.lifetime[0], r[5]);
        assertEquals(flecks.size[0], r[6]);
        assertEquals(flecks.variant[0], r[7]);
        assertEquals(flecks.turnCos[0], r[8]);
        assertEquals(flecks.turnSin[0], r[9]);
        assertEquals(0x00F000A0, (int) r[10], "the light comes back whole");
    }

    @Test
    void aStreaksRecordHoldsWhereItWasAndIs() {
        var streaks = new WindowStreaks(10);
        streaks.add(CAMERA_X, 72.0, CAMERA_Z, SnowFlecks.FACE_NORTH, 70.0, true, 0.5F, 0x00F000F0, Randomizer.create(5));
        for (int t = 0; t < 30; t++)
            streaks.tick(Randomizer.create(t), (x, y, z) -> {});
        assumeAny(streaks.count());
        var packed = streaks.packed(CAMERA_X, CAMERA_Y, CAMERA_Z);
        float[] r = packed.data;
        assertEquals(streaks.prevY[0] - packed.anchorY, r[1], 1e-4);
        assertEquals(streaks.y[0] - packed.anchorY, r[7], 1e-4);
        assertEquals(streaks.prevTrailTop[0] - packed.anchorY, r[8], 1e-4);
        assertEquals(streaks.trailTop[0] - packed.anchorY, r[9], 1e-4);
        assertEquals(SnowFlecks.FACE_NORTH, r[3]);
    }

    @Test
    void aSpotsRecordHoldsItsCorners() {
        var spots = new DampSpots(10);
        spots.add(CAMERA_X - 4, 63.0, CAMERA_Z + 2, 0.8F, Randomizer.create(7));
        var packed = spots.packed(CAMERA_X, CAMERA_Y, CAMERA_Z);
        float[] r = packed.data;
        assertEquals(spots.peak[0], r[3]);
        assertEquals(spots.cornerX[0], r[6]);
        assertEquals(spots.cornerZ[0], r[7]);
    }

    @Test
    void packedAgainOnlyOnceSaidToHaveChanged() {
        var spots = new DampSpots(10);
        spots.add(CAMERA_X, 63.0, CAMERA_Z, 0.8F, Randomizer.create(7));
        var packed = spots.packed(CAMERA_X, CAMERA_Y, CAMERA_Z);
        long version = packed.version;
        spots.add(CAMERA_X + 1, 63.0, CAMERA_Z, 0.8F, Randomizer.create(8));
        assertEquals(version, spots.packed(CAMERA_X, CAMERA_Y, CAMERA_Z).version, "not said to have changed: kept");
        assertEquals(1, packed.count);
        packed.invalidate();
        assertNotEquals(version, spots.packed(CAMERA_X, CAMERA_Y, CAMERA_Z).version);
        assertEquals(2, packed.count);
    }

    @Test
    void theQuadsThreadPacksEverythingWhenTheGpuMakesIt() {
        var field = new PrecipitationField(100);
        var flecks = new SnowFlecks(10);
        var streaks = new WindowStreaks(10);
        var spots = new DampSpots(10);
        flecks.add(CAMERA_X, 64.0, CAMERA_Z, 0.5F, 0.5F, 0, Randomizer.create(1));
        spots.add(CAMERA_X, 63.0, CAMERA_Z, 0.5F, Randomizer.create(2));
        var sources = new DropMeshes.Sources(field, flecks, streaks, spots);
        var view = new PrecipitationRenderer.View(new Vec3(CAMERA_X, CAMERA_Y, CAMERA_Z), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1));
        var style = new DropMeshes.Style(List.of(), 1, new net.minecraft.client.renderer.texture.TextureAtlasSprite[0], DropScales.USUAL, RainColor.ofTint(15), null, true);
        DropMeshes.pack(new DropMeshes.Request(sources, view, 0.5F, style));
        assertEquals(1, flecks.packed.count);
        assertEquals(1, spots.packed.count);
        long version = spots.packed.version;
        // A later frame's build in the same tick finds them packed
        DropMeshes.pack(new DropMeshes.Request(sources, view, 0.75F, style));
        assertEquals(version, spots.packed.version);
        // The next tick says they changed
        sources.marksChanged();
        DropMeshes.pack(new DropMeshes.Request(sources, view, 0.25F, style));
        assertNotEquals(version, spots.packed.version);
    }

    private static void assumeAny(int count) {
        org.junit.jupiter.api.Assumptions.assumeTrue(count > 0, "the streak ran off before it could be checked");
    }

    @Test
    void theUniformBlockIsWhatIsPacked() throws IOException {
        // MarkInfo's room, with the pipeline's MAX_FLAKES, against what GpuMarks packs
        var defines = PrecipitationShaders.GPU_MARKS.pipeline().getShaderDefines().values();
        assertEquals(GpuMarks.INFO_SIZE, ShaderSources.blockSize(shader(), "MarkInfo", defines));
    }
}
