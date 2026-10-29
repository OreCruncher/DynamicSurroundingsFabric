package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The drops are written straight into memory: the bytes must be those a BufferBuilder writes for the same corners, or
 * the pipeline reads them wrong.
 */
public class ParticleQuadsTests {

    // Corners with colours and lights that would show a byte or channel out of place, and alphas rounded at an edge
    private static final float[][] CORNERS = {
            {1.5F, -2.25F, 3F, 0.125F, 0.875F, 0.1F, 0.2F, 0.3F, 0.4F},
            {-7F, 0F, 0.5F, -1F, -1F, 1F, 0.5F, 0F, 0F},
            {100F, 64.5F, -12.75F, 0.5F, 0.25F, 0.88F, 0.94F, 1F, 0.999F},
            {0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 1F},
    };
    private static final int[] LIGHTS = {0xF000F0, 0x00A00030, 0, 0x00F00000};

    @Test
    void theBytesAreThoseABufferBuilderWrites() {
        byte[] expected;
        try (var bytes = new ByteBufferBuilder(1024)) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);
            for (int k = 0; k < CORNERS.length; k++) {
                var c = CORNERS[k];
                builder.addVertex(c[0], c[1], c[2]).setUv(c[3], c[4]).setColor(c[5], c[6], c[7], c[8]).setLight(LIGHTS[k]);
            }
            try (var mesh = builder.buildOrThrow()) {
                var buffer = mesh.vertexBuffer();
                expected = new byte[buffer.remaining()];
                buffer.get(expected);
            }
        }

        byte[] actual;
        try (var quads = new ParticleQuads()) {
            quads.begin(1);
            for (int k = 0; k < CORNERS.length; k++) {
                var c = CORNERS[k];
                quads.vertex(c[0], c[1], c[2], c[3], c[4], ParticleQuads.color(c[5], c[6], c[7], c[8]), LIGHTS[k]);
            }
            assertEquals(CORNERS.length, quads.vertexCount());
            var buffer = quads.vertices();
            actual = new byte[buffer.remaining()];
            buffer.get(actual);
        }

        assertArrayEquals(expected, actual);
    }

    @Test
    void itGrowsToFitAndStartsOverEachTime() {
        try (var quads = new ParticleQuads()) {
            quads.begin(1);
            for (int k = 0; k < 4; k++)
                quads.vertex(k, k, k, 0F, 0F, 0, 0);
            quads.begin(1000);
            assertEquals(0, quads.vertexCount(), "starts over");
            for (int k = 0; k < 4000; k++)
                quads.vertex(k, -k, k, 0.5F, 0.5F, -1, 0xF000F0);
            assertEquals(4000, quads.vertexCount());
            assertEquals(4000 * ParticleQuads.VERTEX_SIZE, quads.vertices().remaining());
            assertEquals(3999F, quads.vertices().getFloat(3999 * ParticleQuads.VERTEX_SIZE), "the last corner's x");
        }
    }
}
