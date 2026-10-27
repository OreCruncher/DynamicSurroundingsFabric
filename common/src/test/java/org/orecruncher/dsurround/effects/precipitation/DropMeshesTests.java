package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.Wind;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for building the drops' quads away from the render thread (see {@link DropMeshes}): what it builds there is
 * what the render thread would have, while the drops' next tick is stepped.
 */
public class DropMeshesTests {

    private static final int GROUND = 64;
    private static final double CAMERA_Y = 70.5;
    // Looking north and a little down
    private static final PrecipitationRenderer.View VIEW = new PrecipitationRenderer.View(new Vec3(0.5, CAMERA_Y, 0.5),
            new Vector3f(-1F, 0F, 0F), new Vector3f(0F, 0.97F, -0.24F), new Vector3f(0F, -0.24F, -0.97F));

    // Rain to the west, snow to the east, on flat ground
    private record Columns() implements IColumnSource {
        @Override
        public int groundHeight(int x, int z) {
            return GROUND;
        }

        @Override
        public PrecipitationKind precipitation(int x, int y, int z) {
            return x < 0 ? PrecipitationKind.RAIN : PrecipitationKind.SNOW;
        }

        @Override
        public int light(int x, int y, int z) {
            return 0xF000F0;
        }

        @Override
        public int dustColor(int x, int y, int z) {
            return NOT_A_DUST_SOURCE;
        }

        @Override
        public int fallbackDustColor(int x, int y, int z) {
            return NOT_A_DUST_SOURCE;
        }
    }

    private static byte[] build(PrecipitationField field) {
        try (var bytes = new ByteBufferBuilder(4096)) {
            var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            PrecipitationRenderer.build(field, builder, VIEW, 0.5F, false, List.of(), 1, new TextureAtlasSprite[0],
                    DropScales.USUAL, RainColor.ofTint(15));
            try (var mesh = builder.build()) {
                assertNotNull(mesh, "drops to draw");
                ByteBuffer vertices = mesh.vertexBuffer();
                var copy = new byte[vertices.remaining()];
                vertices.get(copy);
                return copy;
            }
        }
    }

    @Test
    void dropsBuiltWhileTheStepRunsAreTheOnesDrawn() throws Exception {
        var columns = new Columns();
        var field = new PrecipitationField(2000);
        var random = Randomizer.create(3);
        for (int t = 0; t < 20; t++)
            field.tick(columns, 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random);
        var expected = build(field);

        // The step and the build each on a thread of their own, as in the game, where the build may follow the step
        // on the same one; built over and over while the step runs
        var stepper = Executors.newSingleThreadExecutor();
        var builders = Executors.newSingleThreadExecutor();
        try {
            field.start(columns, 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random, stepper);
            for (int i = 0; i < 20; i++)
                assertArrayEquals(expected, CompletableFuture.supplyAsync(() -> build(field), builders).get());
            field.finish(columns);
        } finally {
            stepper.shutdown();
            builders.shutdown();
        }
        assertFalse(Arrays.equals(expected, build(field)), "the drops moved once the step finished");
    }
}
