// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.aurora.RealisticAuroraRenderer;
import org.orecruncher.dsurround.effects.groundfog.GroundFogVolume;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the passes over the whole of what they draw to: the targets at a share of the screen, and the pipelines.
 */
public class ScreenPassesTests {

    // The pipelines take their ids from Constants, which needs the game's registries
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aReducedTargetCoversEveryPixelOfTheFullOne() {
        assertEquals(960, ScreenPasses.Reduced.size(1920, 2));
        assertEquals(360, ScreenPasses.Reduced.size(1080, 3));
        // Rounded up: the last, partly covered pixels still have one over them
        assertEquals(641, ScreenPasses.Reduced.size(1921, 3));
        assertEquals(1, ScreenPasses.Reduced.size(1, 4));
        assertEquals(1366, ScreenPasses.Reduced.size(1366, 1));
        for (int full = 1; full < 200; full++)
            for (int scale = 1; scale <= 4; scale++) {
                int size = ScreenPasses.Reduced.size(full, scale);
                assertTrue(size * scale >= full && (size - 1) * scale < full, full + " at 1/" + scale);
            }
    }

    @Test
    void everyScreenPassDrawsOneUnculledTriangle() {
        List<RenderPipeline> pipelines = new ArrayList<>();
        GroundFogVolume.SHADERS.forEach(shader -> pipelines.add(shader.pipeline()));
        RealisticAuroraRenderer.SHADERS.forEach(shader -> pipelines.add(shader.pipeline()));
        assertFalse(pipelines.isEmpty());
        for (var pipeline : pipelines) {
            assertEquals(PrimitiveTopology.TRIANGLES, pipeline.getPrimitiveTopology(), pipeline.getLocation().toString());
            assertFalse(pipeline.isCull(), pipeline.getLocation().toString());
            // Its slots for vertex buffers are all empty
            assertTrue(pipeline.getVertexFormatBindings().stream().allMatch(Objects::isNull), pipeline.getLocation() + " takes no vertices");
        }
    }
}
