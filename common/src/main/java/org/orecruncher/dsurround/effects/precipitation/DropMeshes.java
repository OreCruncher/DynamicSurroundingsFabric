package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * The falling drops' quads, built ahead on another thread: as the level starts to be drawn ({@link #begin}), once the
 * camera is set for the frame, so they are built while the render thread draws the terrain, and the weather's pass only
 * draws them ({@link #draw}). Building them, every drop facing the camera and placed between its ticks, was the largest
 * part of the weather's time on the render thread.
 * <p>
 * Built for the camera and partial tick as the level began; if the weather is drawn for another (a mod drawing the level
 * again from elsewhere: a portal, a mirror), or drawn twice, they are built there and then, on the render thread, as
 * before. Each kind, the rain and snow and the dust, is built into a buffer of its own and drawn at most once.
 * <p>
 * The build reads the drops drawn, which only the client tick changes, when it finishes the drops' step; the tick waits
 * for a build under way, and drops it, first ({@link #discard}). Render thread only, but for the build itself.
 */
public final class DropMeshes {

    // Room to start with, in bytes, for each kind: a few thousand drops, before the buffer grows
    private static final int INITIAL_BYTES = 256 * 1024;

    /**
     * What a frame's drops were built for: drawn only for the same
     */
    private record Request(PrecipitationField field, PrecipitationRenderer.View view, float partialTick,
                           List<Snowflakes.Flake> snowflakes, int largest, TextureAtlasSprite[] dust, DropScales scales,
                           RainColor rainColor) {

        boolean isFor(PrecipitationField field, Camera camera, float partialTick, List<Snowflakes.Flake> snowflakes,
                      int largest, TextureAtlasSprite[] dust, DropScales scales, RainColor rainColor) {
            return this.field == field && this.partialTick == partialTick && this.snowflakes == snowflakes
                    && this.largest == largest && this.dust == dust && this.scales.equals(scales)
                    && this.rainColor.equals(rainColor) && this.view.isOf(camera);
        }
    }

    private final ByteBufferBuilder dropBytes = new ByteBufferBuilder(INITIAL_BYTES);
    private final ByteBufferBuilder dustBytes = new ByteBufferBuilder(INITIAL_BYTES);
    @Nullable
    private Request request;
    @Nullable
    private CompletableFuture<Void> pending;
    // Set by the build, read once it is joined; null once drawn, or if there were none
    @Nullable
    private MeshData drops;
    @Nullable
    private MeshData dust;
    private boolean dropsTaken;
    private boolean dustTaken;
    // Kinds drawn from what was built ahead, and built on the render thread instead, since the diagnostics last asked
    private int ahead;
    private int behind;

    /**
     * Starts building the frame's drops on {@code executor}, for the camera as it is now. What was built for the frame
     * before and not drawn is dropped.
     */
    public void begin(PrecipitationField field, Camera camera, float partialTick, List<Snowflakes.Flake> snowflakes,
                      int largest, TextureAtlasSprite[] dust, DropScales scales, RainColor rainColor, Executor executor) {
        this.discard();
        if (field.count() == 0)
            return;
        var request = new Request(field, PrecipitationRenderer.View.of(camera), partialTick, snowflakes, largest, dust,
                scales, rainColor);
        this.request = request;
        this.pending = CompletableFuture.runAsync(() -> this.build(request), executor);
    }

    // On the executor's thread
    private void build(Request request) {
        this.drops = build(this.dropBytes, request, false);
        this.dust = build(this.dustBytes, request, true);
    }

    @Nullable
    private static MeshData build(ByteBufferBuilder bytes, Request request, boolean dustOnly) {
        if (!PrecipitationRenderer.hasAny(request.field(), dustOnly))
            return null;
        var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        PrecipitationRenderer.build(request.field(), builder, request.view(), request.partialTick(), dustOnly,
                request.snowflakes(), request.largest(), request.dust(), request.scales(), request.rainColor());
        return builder.build();
    }

    /**
     * Draws the drops of a kind, as {@link PrecipitationRenderer#draw}: those built ahead, if they were built for this
     * camera, partial tick and settings and not drawn yet; otherwise built now
     *
     * @return how many were drawn
     * @throws RuntimeException what the build threw, if it failed
     */
    public int draw(PrecipitationField field, Tesselator tesselator, Camera camera, float partialTick, boolean dustOnly,
                    List<Snowflakes.Flake> snowflakes, int largest, TextureAtlasSprite[] dust, DropScales scales,
                    RainColor rainColor) {
        var request = this.request;
        boolean taken = dustOnly ? this.dustTaken : this.dropsTaken;
        if (request == null || taken || !request.isFor(field, camera, partialTick, snowflakes, largest, dust, scales, rainColor)) {
            this.behind++;
            return PrecipitationRenderer.draw(field, tesselator, camera, partialTick, dustOnly, snowflakes, largest, dust, scales, rainColor);
        }

        this.join();
        this.ahead++;
        MeshData mesh;
        if (dustOnly) {
            mesh = this.dust;
            this.dust = null;
            this.dustTaken = true;
        } else {
            mesh = this.drops;
            this.drops = null;
            this.dropsTaken = true;
        }
        if (mesh == null)
            return 0;
        int drawn = mesh.drawState().vertexCount() / 4;
        BufferUploader.drawWithShader(mesh);
        return drawn;
    }

    // Waits for the build under way, if there is one
    private void join() {
        var running = this.pending;
        if (running == null)
            return;
        this.pending = null;
        try {
            running.join();
        } catch (CompletionException e) {
            this.discard();
            if (e.getCause() instanceof RuntimeException failure)
                throw failure;
            if (e.getCause() instanceof Error error)
                throw error;
            throw e;
        }
    }

    /**
     * Waits for a build under way and drops what was built and not drawn: before the drops drawn change, and before
     * the next frame's
     */
    public void discard() {
        var running = this.pending;
        this.pending = null;
        if (running != null) {
            try {
                running.join();
            } catch (CompletionException ignored) {
                // Dropped anyway
            }
        }
        if (this.drops != null) {
            this.drops.close();
            this.drops = null;
        }
        if (this.dust != null) {
            this.dust.close();
            this.dust = null;
        }
        this.request = null;
        this.dropsTaken = false;
        this.dustTaken = false;
    }

    /**
     * The share of the kinds drawn that were built ahead since the last time asked, 0 to 1; NaN if none were drawn
     */
    public float aheadShare() {
        int total = this.ahead + this.behind;
        float share = total == 0 ? Float.NaN : this.ahead / (float) total;
        this.ahead = 0;
        this.behind = 0;
        return share;
    }
}
