// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.QuadWriter;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.Wind;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for leaving out of the weather's quads what is past the edges of the view ({@link PrecipitationRenderer.Sides}):
 * nothing that could be seen is left out, whatever the projection, and most of what can't be is.
 */
public class ViewSidesTests {

    private static final double CAMERA_Y = 70.5;
    private static final float FOV = (float) Math.toRadians(70);
    private static final float ASPECT = 16F / 9F;
    // As the game makes it: reverse-Z, near and far swapped
    private static final Matrix4fc PROJECTION = new Matrix4f().perspective(FOV, ASPECT, 1000F, 0.05F, true);

    // The projections a frame can have: plain, a wider field of view, tilted by being hurt, bobbing as the player walks
    private static final List<Matrix4fc> PROJECTIONS = List.of(
            PROJECTION,
            new Matrix4f().perspective((float) Math.toRadians(110), ASPECT, 1000F, 0.05F, true),
            new Matrix4f(PROJECTION).rotateZ((float) Math.toRadians(14)),
            new Matrix4f(PROJECTION).translate(0.05F, -0.08F, 0F).rotateZ((float) Math.toRadians(0.5)).rotateX((float) Math.toRadians(0.5)));

    // A camera turned and placed as the game does it
    private static final class PlacedCamera extends Camera {
        PlacedCamera(float yRot, float xRot) {
            this.setRotation(yRot, xRot);
            this.setPosition(0.5, CAMERA_Y, 0.5);
        }
    }

    // Whether a point relative to the camera is on screen: in front, inside the edges
    private static boolean onScreen(Matrix4fc projection, Matrix4fc viewRotation, float x, float y, float z) {
        var clip = new Matrix4f(projection).mul(viewRotation).transform(new Vector4f(x, y, z, 1F));
        return clip.w > 0F && Math.abs(clip.x) <= clip.w && Math.abs(clip.y) <= clip.w;
    }

    @Test
    void aheadIsSeenAndFarPastAnEdgeIsNot() {
        // Yaw 180 faces north
        var camera = new PlacedCamera(180F, 0F);
        var view = PrecipitationRenderer.View.of(camera, PROJECTION);
        assertNotNull(view.sides());
        // Looking north, toward negative z: the right edge is at about 1.245 blocks across a block ahead
        assertTrue(view.sees(0F, 0F, -10F, 0.1F), "ahead");
        assertTrue(view.sees(12F, 0F, -10F, 0.1F), "just inside the right edge");
        assertTrue(view.sees(13F, 0F, -10F, 1F), "just past it, but reaching in");
        assertFalse(view.sees(17F, 0F, -10F, 0.1F), "well past it");
        assertFalse(view.sees(-17F, 0F, -10F, 0.1F), "well past the left edge");
        assertFalse(view.sees(0F, 12F, -10F, 0.1F), "well over the top");
        assertFalse(view.sees(0F, -12F, -10F, 0.1F), "well under the bottom");
        assertFalse(view.sees(0F, 0F, 10F, 0.1F), "behind");
    }

    @Test
    void nothingOnScreenIsLeftOut() {
        var random = Randomizer.create(11);
        for (var projection : PROJECTIONS) {
            for (float yRot : new float[]{0F, 37.5F, 180F}) {
                for (float xRot : new float[]{-60F, 5F, 80F}) {
                    var camera = new PlacedCamera(yRot, xRot);
                    var viewRotation = camera.getViewRotationMatrix(new Matrix4f());
                    var view = PrecipitationRenderer.View.of(camera, projection);
                    int onScreen = 0, seen = 0;
                    for (int i = 0; i < 20000; i++) {
                        float x = (random.nextFloat() - 0.5F) * 48F;
                        float y = (random.nextFloat() - 0.5F) * 48F;
                        float z = (random.nextFloat() - 0.5F) * 48F;
                        boolean visible = onScreen(projection, viewRotation, x, y, z);
                        boolean sees = view.sees(x, y, z, 0F);
                        if (visible) {
                            onScreen++;
                            assertTrue(sees, "(%s, %s, %s) on screen, turned %s, %s".formatted(x, y, z, yRot, xRot));
                        }
                        if (sees)
                            seen++;
                    }
                    // The slack lets in a little more than is on screen, not much more
                    assertTrue(seen < onScreen * 1.4F + 50, "seen %d, on screen %d, turned %s, %s".formatted(seen, onScreen, yRot, xRot));
                }
            }
        }
    }

    @Test
    void aProjectionThatIsntOneLeavesOutOnlyWhatIsBehind() {
        var camera = new PlacedCamera(180F, 0F);
        var view = PrecipitationRenderer.View.of(camera, new Matrix4f().zero());
        assertNull(view.sides());
        assertTrue(view.sees(500F, 0F, -1F, 0.1F), "beside: no sides to be past");
        assertFalse(view.sees(0F, 0F, 10F, 0.1F), "behind");
        assertNull(PrecipitationRenderer.View.of(camera).sides(), "none given");
    }

    // The quads written, as the bytes of their four corners
    private static Set<String> quads(QuadWriter quads) {
        var set = new HashSet<String>();
        ByteBuffer vertices = quads.vertices();
        int size = quads.format().vertexSize() * 4;
        var quad = new byte[size];
        for (int at = vertices.position(); at + size <= vertices.limit(); at += size) {
            vertices.get(at, quad);
            set.add(Arrays.toString(quad));
        }
        return set;
    }

    // Whether any corner of a quad, as its bytes, is on screen
    private static boolean onScreen(String quad, int vertexSize, Matrix4fc projection, Matrix4fc viewRotation) {
        var parts = quad.substring(1, quad.length() - 1).split(", ");
        var bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++)
            bytes[i] = Byte.parseByte(parts[i]);
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
        for (int corner = 0; corner < 4; corner++) {
            int at = corner * vertexSize;
            if (onScreen(projection, viewRotation, buffer.getFloat(at), buffer.getFloat(at + 4), buffer.getFloat(at + 8)))
                return true;
        }
        return false;
    }

    // Every quad written without the sides that has a corner on screen is written with them, and fewer are written
    private static void keepsWhatIsOnScreen(QuadWriter.Format format, Consumer<QuadWriter> withSides,
                                            Consumer<QuadWriter> withoutSides, Matrix4fc projection, Matrix4fc viewRotation,
                                            String what) {
        try (var culled = new QuadWriter(format); var all = new QuadWriter(format)) {
            withSides.accept(culled);
            withoutSides.accept(all);
            var kept = quads(culled);
            var written = quads(all);
            assertFalse(written.isEmpty(), what + ": something to draw");
            int visible = 0;
            for (var quad : written) {
                if (onScreen(quad, format.vertexSize(), projection, viewRotation)) {
                    visible++;
                    assertTrue(kept.contains(quad), what + ": a quad on screen left out");
                }
            }
            assertTrue(visible > 0, what + ": something on screen");
            assertTrue(kept.size() < written.size(), what + ": %d of %d kept".formatted(kept.size(), written.size()));
        }
    }

    private record Columns(PrecipitationKind kind) implements IColumnSource {
        public int groundHeight(int x, int z) {
            return 64;
        }

        public PrecipitationKind precipitation(int x, int y, int z) {
            return this.kind;
        }

        public int light(int x, int y, int z) {
            return 0xF000F0;
        }

        public int dustColor(int x, int y, int z) {
            return NOT_A_DUST_SOURCE;
        }

        public int fallbackDustColor(int x, int y, int z) {
            return NOT_A_DUST_SOURCE;
        }
    }

    @Test
    void theWeathersQuadsOnScreenAreAllWritten() {
        var camera = new PlacedCamera(37.5F, 5F);
        var viewRotation = camera.getViewRotationMatrix(new Matrix4f());
        var tilted = PROJECTIONS.get(2);
        var withSides = PrecipitationRenderer.View.of(camera, tilted);
        var withoutSides = PrecipitationRenderer.View.of(camera);

        for (var kind : new PrecipitationKind[]{PrecipitationKind.RAIN, PrecipitationKind.SNOW}) {
            var field = new PrecipitationField(PrecipitationField.forArea(6000, 20), 20);
            var random = Randomizer.create(3);
            for (int t = 0; t < 100; t++)
                field.tick(new Columns(kind), 0.5, CAMERA_Y, 0.5, 1F, 1F, new Wind(), random);
            keepsWhatIsOnScreen(QuadWriter.Format.PARTICLE,
                    quads -> PrecipitationRenderer.build(field, quads, withSides, 0.5F, false, List.of(), 1,
                            new TextureAtlasSprite[0], DropScales.USUAL, RainColor.ofTint(15)),
                    quads -> PrecipitationRenderer.build(field, quads, withoutSides, 0.5F, false, List.of(), 1,
                            new TextureAtlasSprite[0], DropScales.USUAL, RainColor.ofTint(15)),
                    tilted, viewRotation, kind.toString());
        }

        // What was left on the ground and on windows, all around the camera
        var random = Randomizer.create(5);
        var flecks = new SnowFlecks(2000);
        var streaks = new WindowStreaks(2000);
        var spots = new DampSpots(2000);
        for (int i = 0; i < 1500; i++) {
            double x = 0.5 + (random.nextFloat() - 0.5F) * 40F, z = 0.5 + (random.nextFloat() - 0.5F) * 40F;
            flecks.add(x, 64, z, random.nextFloat(), random.nextFloat(), 0xF000F0, random);
            spots.add(x, 64, z, random.nextFloat(), random);
            streaks.add(x, 66 + random.nextFloat() * 3F, z, SnowFlecks.FACE_SOUTH, 64, false, random.nextFloat(), 0xF000F0, random);
        }
        keepsWhatIsOnScreen(QuadWriter.Format.PARTICLE,
                quads -> PrecipitationRenderer.build(flecks, quads, withSides, 0.5F, List.of(), 1, 1F),
                quads -> PrecipitationRenderer.build(flecks, quads, withoutSides, 0.5F, List.of(), 1, 1F),
                tilted, viewRotation, "snow flecks");
        keepsWhatIsOnScreen(QuadWriter.Format.PARTICLE,
                quads -> PrecipitationRenderer.build(streaks, quads, withSides, 0.5F, RainColor.ofTint(15)),
                quads -> PrecipitationRenderer.build(streaks, quads, withoutSides, 0.5F, RainColor.ofTint(15)),
                tilted, viewRotation, "window streaks");
        var id = Identifier.fromNamespaceAndPath("dsurround", "test");
        var sprite = new TextureAtlasSprite(id, new SpriteContents(id, new FrameSize(1, 1), new NativeImage(1, 1, false)), 16, 16, 0, 0, 0) {
        };
        keepsWhatIsOnScreen(QuadWriter.Format.POSITION_TEX_COLOR,
                quads -> PrecipitationRenderer.build(spots, quads, withSides, 0.5F, sprite),
                quads -> PrecipitationRenderer.build(spots, quads, withoutSides, 0.5F, sprite),
                tilted, viewRotation, "damp spots");
    }
}
