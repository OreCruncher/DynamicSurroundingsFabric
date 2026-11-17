// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.QuadWriter;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.runtime.weather.Wind;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
        try (var quads = new QuadWriter(QuadWriter.Format.PARTICLE)) {
            PrecipitationRenderer.build(field, quads, VIEW, 0.5F, false, List.of(), 1, new TextureAtlasSprite[0],
                    DropScales.USUAL, RainColor.ofTint(15));
            assertTrue(quads.vertexCount() > 0, "drops to draw");
            ByteBuffer vertices = quads.vertices();
            var copy = new byte[vertices.remaining()];
            vertices.get(copy);
            return copy;
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

    private static final TextureAtlasSprite[] NO_DUST = new TextureAtlasSprite[0];
    private static final DropMeshes.Style STYLE = new DropMeshes.Style(List.of(), 1, NO_DUST, DropScales.USUAL,
            RainColor.ofTint(15), null);

    private static DropMeshes.Sources sources(PrecipitationField field) {
        return new DropMeshes.Sources(field, new SnowFlecks(10), new WindowStreaks(10), new DampSpots(10));
    }

    @Test
    void theSameFrameMatches() {
        var sources = sources(new PrecipitationField(10));
        var view = new PrecipitationRenderer.View(VIEW.position(), new Vector3f(VIEW.left()), new Vector3f(VIEW.up()),
                new Vector3f(VIEW.look()));
        var style = new DropMeshes.Style(STYLE.snowflakes(), 1, NO_DUST, new DropScales(1F, 1F), RainColor.ofTint(15), null);
        var built = new DropMeshes.Request(sources, VIEW, 0.5F, STYLE);
        assertNull(built.mismatch(new DropMeshes.Request(new DropMeshes.Sources(sources.field(), sources.flecks(),
                sources.streaks(), sources.spots()), view, 0.5F, style)));
    }

    @Test
    void sayWhatDiffers() {
        var sources = sources(new PrecipitationField(10));
        var built = new DropMeshes.Request(sources, VIEW, 0.5F, STYLE);

        var moved = new PrecipitationRenderer.View(VIEW.position().add(0, 1e-9, 0), VIEW.left(), VIEW.up(), VIEW.look());
        var mismatch = built.mismatch(new DropMeshes.Request(sources, moved, 0.5F, STYLE));
        assertNotNull(mismatch);
        assertEquals(DropMeshes.Behind.CAMERA_MOVED, mismatch.kind());

        var turned = new PrecipitationRenderer.View(VIEW.position(), VIEW.left(), VIEW.up(),
                new Vector3f(VIEW.look()).add(0F, 1e-6F, 0F));
        mismatch = built.mismatch(new DropMeshes.Request(sources, turned, 0.5F, STYLE));
        assertNotNull(mismatch);
        assertEquals(DropMeshes.Behind.CAMERA_TURNED, mismatch.kind());

        mismatch = built.mismatch(new DropMeshes.Request(sources, VIEW, 0.75F, STYLE));
        assertNotNull(mismatch);
        assertEquals(DropMeshes.Behind.PARTIAL_TICK, mismatch.kind());
        assertEquals("0.5 to 0.75", mismatch.detail());

        var bigger = new DropMeshes.Style(STYLE.snowflakes(), 1, NO_DUST, new DropScales(2F, 1F), STYLE.rainColor(), null);
        mismatch = built.mismatch(new DropMeshes.Request(sources, VIEW, 0.5F, bigger));
        assertNotNull(mismatch);
        assertEquals(DropMeshes.Behind.SETTINGS, mismatch.kind());

        var otherFlecks = new DropMeshes.Sources(sources.field(), new SnowFlecks(10), sources.streaks(), sources.spots());
        mismatch = built.mismatch(new DropMeshes.Request(otherFlecks, VIEW, 0.5F, STYLE));
        assertNotNull(mismatch);
        assertEquals(DropMeshes.Behind.OTHER_SOURCES, mismatch.kind());
        assertEquals("the snow flecks replaced", mismatch.detail());
    }

    @Test
    void eachKindIsThereOnlyWithSomethingToDraw() {
        var sources = sources(new PrecipitationField(10));
        assertTrue(sources.isEmpty());
        var random = Randomizer.create(9);
        sources.flecks().add(2.5, GROUND, 3.5, 0.5F, 0.5F, 0xF000F0, random);
        sources.spots().add(1.5, GROUND, 1.5, 0.5F, random);
        assertFalse(sources.isEmpty());
        assertTrue(sources.hasAny(DropMeshes.Kind.FLECKS));
        assertTrue(sources.hasAny(DropMeshes.Kind.SPOTS));
        assertFalse(sources.hasAny(DropMeshes.Kind.DROPS));
        assertFalse(sources.hasAny(DropMeshes.Kind.DUST));
        assertFalse(sources.hasAny(DropMeshes.Kind.STREAKS));
    }

    @Test
    void snowFlecksAreBuiltAroundTheCamera() {
        var sources = sources(new PrecipitationField(10));
        var random = Randomizer.create(7);
        sources.flecks().add(2.5, GROUND, 3.5, 0.5F, 0.5F, 0xF000F0, random);
        sources.flecks().add(-1.5, GROUND, -4.5, 0.5F, 0.5F, 0xF000F0, random);
        try (var quads = new QuadWriter(DropMeshes.Kind.FLECKS.format)) {
            DropMeshes.build(DropMeshes.Kind.FLECKS, quads, new DropMeshes.Request(sources, VIEW, 0.5F, STYLE));
            assertEquals(8, quads.vertexCount(), "flecks to draw");
            // The first corner lies on the ground, below the camera, near the first fleck
            var vertices = quads.vertices();
            assertEquals(2F, vertices.getFloat(0), 0.2F);
            assertEquals((float) (GROUND - CAMERA_Y), vertices.getFloat(4), 0.02F);
            assertEquals(3F, vertices.getFloat(8), 0.2F);
        }
    }

    @Test
    void windowStreaksAreBuiltAroundTheCamera() {
        var sources = sources(new PrecipitationField(10));
        var random = Randomizer.create(11);
        // On the north face of a window to the north of the camera, a little above the bottom of the glass
        sources.streaks().add(0.5, GROUND + 2.5, -3.0, SnowFlecks.FACE_NORTH, GROUND + 2.0, false, 0.5F, 0xF000F0, random);
        try (var quads = new QuadWriter(DropMeshes.Kind.STREAKS.format)) {
            DropMeshes.build(DropMeshes.Kind.STREAKS, quads, new DropMeshes.Request(sources, VIEW, 0.5F, STYLE));
            // A new drop fades in from nothing, so it may not be drawn yet; when it is, it is just off the window's face
            if (quads.vertexCount() > 0)
                assertEquals(-3.5F, quads.vertices().getFloat(8), 0.02F);
        }
    }

    @Test
    void eachReasonIsLoggedOnce() {
        var log = new RecordingLog();
        var meshes = new DropMeshes(log);
        meshes.report(new DropMeshes.Mismatch(DropMeshes.Behind.PARTIAL_TICK, "0.5 to 0.75"));
        meshes.report(new DropMeshes.Mismatch(DropMeshes.Behind.PARTIAL_TICK, "0.25 to 0.5"));
        meshes.report(new DropMeshes.Mismatch(DropMeshes.Behind.DRAWN_TWICE, "the dust"));
        var logged = log.at(IModLog.Level.INFO);
        assertEquals(2, logged.size());
        assertTrue(logged.getFirst().message().contains("0.5 to 0.75"), logged.getFirst().message());
    }

    // A field with drops in it
    private static PrecipitationField raining() {
        var field = new PrecipitationField(200);
        var random = Randomizer.create(3);
        for (int t = 0; t < 20; t++)
            field.tick(new Columns(), 0, CAMERA_Y, 0, 1F, 1F, new Wind(), random);
        return field;
    }

    @Test
    void aBuildNotDoneInTimeIsBuiltOnTheRenderThreadAndNotLogged() {
        var log = new RecordingLog();
        var meshes = new DropMeshes(log);
        var sources = sources(raining());
        var camera = new TurnedCamera(0F, 0F);
        // A build's thread that doesn't run it until told: starved
        var held = new ArrayList<Runnable>();
        meshes.begin(sources, camera, 0.5F, STYLE, held::add);
        var now = new DropMeshes.Request(sources, PrecipitationRenderer.View.of(camera), 0.5F, STYLE);

        long start = System.nanoTime();
        meshes.await();
        long waited = System.nanoTime() - start;
        assertTrue(waited >= DropMeshes.WAIT_NANOS, "waited " + waited + " ns");
        assertTrue(waited < 1_000_000_000L, "gave up waiting: " + waited + " ns");

        var late = meshes.mismatch(DropMeshes.Kind.DROPS, now);
        assertNotNull(late);
        assertEquals(DropMeshes.Behind.LATE, late.kind());
        meshes.report(late);
        assertTrue(log.at(IModLog.Level.INFO).isEmpty(), "a late build isn't logged");

        // Once it has run, what it built is drawn
        held.forEach(Runnable::run);
        assertNull(meshes.mismatch(DropMeshes.Kind.DROPS, now));
        meshes.discard();
    }

    @Test
    void waitingForABuildDoneOrNoneDoesntWait() {
        var meshes = new DropMeshes(new RecordingLog());
        long start = System.nanoTime();
        meshes.await();
        long waited = System.nanoTime() - start;
        // Built there and then
        meshes.begin(sources(raining()), new TurnedCamera(0F, 0F), 0.5F, STYLE, Runnable::run);
        start = System.nanoTime();
        meshes.await();
        waited += System.nanoTime() - start;
        assertTrue(waited < DropMeshes.WAIT_NANOS, "waited " + waited + " ns");
        meshes.discard();
    }

    // A camera turned as the game turns it
    private static final class TurnedCamera extends Camera {
        TurnedCamera(float yRot, float xRot) {
            this.setRotation(yRot, xRot);
        }
    }

    @Test
    void theViewIsTheCamerasAndAModChangingItsVectorsDoesntChangeIt() {
        var camera = new TurnedCamera(37.5F, 21.25F);
        var view = PrecipitationRenderer.View.of(camera);
        assertEquals(camera.forwardVector(), view.look());
        assertEquals(camera.upVector(), view.up());
        assertEquals(camera.leftVector(), view.left());

        // As Rainbows does to the camera's own vector on 1.21.1, drawing its particle before the weather
        ((Vector3f) camera.forwardVector()).normalize().mul(2F);
        assertEquals(view, PrecipitationRenderer.View.of(camera));
    }
}
