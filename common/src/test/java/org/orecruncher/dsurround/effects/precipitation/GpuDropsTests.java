// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.ShaderSources;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rain and snow made on the GPU ({@link GpuDrops}): each batch stays within what every GPU allows of a
 * texel buffer, and the shader reads the records as {@link PackedDrops} lays them out.
 */
public class GpuDropsTests {

    private static final int FLOATS_A_TEXEL = 4;

    private static String shader() throws IOException {
        return ShaderSources.read("core/precipitation_gpu.vsh");
    }

    @Test
    void aRecordIsAWholeNumberOfTexels() {
        assertEquals(0, PackedDrops.STRIDE % FLOATS_A_TEXEL, "a drop's record must fill whole texels");
    }

    @Test
    void aBatchFitsTheTexelsEveryGpuAllows() {
        // The batches refuse one larger than every GPU allows
        assertDoesNotThrow(() -> new TexelBatches("drops", PackedDrops.STRIDE, GpuDrops.BATCH_DROPS).close());
        assertThrows(IllegalArgumentException.class, () -> new TexelBatches("drops", PackedDrops.STRIDE, TexelBatches.MAX_TEXELS));
    }

    @Test
    void theWidestRangeNeedsMoreThanOneBatch() {
        // Otherwise the batching is never used, and the bench at range 32 checks nothing
        int widest = PrecipitationField.forArea(6000, 32);
        assertTrue(widest > GpuDrops.BATCH_DROPS, widest + " drops fit one batch");
    }

    @Test
    void theShaderReadsAsManyTexelsADropAsTheRecordHolds() throws IOException {
        int texels = PackedDrops.STRIDE / FLOATS_A_TEXEL;
        assertTrue(shader().contains("int at = (gl_VertexIndex >> 2) * " + texels + ";"),
                "the shader's step from one drop to the next must be " + texels + " texels");
    }

    @Test
    void theSnowflakeSizesFillWholeVectors() {
        // Packed four to a vec4 in the uniform block
        assertEquals(0, Snowflakes.MAX_FLAKES % 4);
    }

    @Test
    void theShaderReadsTheRecordInPackedOrder() throws IOException {
        // prevX prevY prevZ moveX | moveY moveZ kind light | age covered lifetime thinning | size pick A B | C
        assertEquals(0, PackedDrops.PREV_X);
        assertEquals(3, PackedDrops.MOVE_X);
        assertEquals(6, PackedDrops.KIND);
        assertEquals(7, PackedDrops.LIGHT);
        assertEquals(8, PackedDrops.AGE);
        assertEquals(9, PackedDrops.COVERED);
        assertEquals(10, PackedDrops.LIFETIME);
        assertEquals(11, PackedDrops.THINNING);
        assertEquals(12, PackedDrops.SIZE);
        assertEquals(13, PackedDrops.PICK);
        assertEquals(14, PackedDrops.A);
        assertEquals(16, PackedDrops.C);
        var text = shader();
        assertTrue(text.contains("float kind = t1.z;"));
        assertTrue(text.contains("int light = int(t1.w);"));
        assertTrue(text.contains("float size = t3.x;"));
    }

    @Test
    void theUniformBlockIsWhatIsPacked() throws IOException {
        // DropInfo's room, with the pipeline's MAX_FLAKES, against what GpuDrops packs
        var defines = PrecipitationShaders.GPU_DROPS.pipeline().getShaderDefines().values();
        assertEquals(GpuDrops.INFO_SIZE, ShaderSources.blockSize(shader(), "DropInfo", defines));
    }
}
