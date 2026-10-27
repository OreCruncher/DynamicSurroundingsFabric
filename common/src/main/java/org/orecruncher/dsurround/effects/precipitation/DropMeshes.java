package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * The falling drops' quads, built ahead on another thread: as the level starts to be drawn ({@link #begin}), once the
 * camera is set for the frame, so they are written while the render thread draws the terrain, and the weather's pass
 * only uploads and draws them ({@link #quads}). Writing them, every drop facing the camera and placed between its
 * ticks, was the largest part of the weather's time on the render thread.
 * <p>
 * Built for the camera and partial tick as the level began; if the weather is drawn for another (a mod drawing the level
 * again from elsewhere: a portal, a mirror), or drawn twice, they are written there and then, on the render thread, as
 * before. Each kind, the rain and snow and the dust, is written into quads of its own and used at most once.
 * <p>
 * The build reads the drops drawn, which only the client tick changes, when it finishes the drops' step; the tick waits
 * for a build under way, and drops it, first ({@link #discard}). Render thread only, but for the build itself.
 */
public final class DropMeshes implements AutoCloseable {

    /**
     * What a frame's drops were built for: used only for the same
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

    private final ParticleQuads drops = new ParticleQuads();
    private final ParticleQuads dust = new ParticleQuads();
    // Written on the render thread when what was built ahead can't be used
    private final ParticleQuads now = new ParticleQuads();
    @Nullable
    private Request request;
    @Nullable
    private CompletableFuture<Void> pending;
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
        build(this.drops, request, false);
        build(this.dust, request, true);
    }

    private static void build(ParticleQuads quads, Request request, boolean dustOnly) {
        PrecipitationRenderer.build(request.field(), quads, request.view(), request.partialTick(), dustOnly,
                request.snowflakes(), request.largest(), request.dust(), request.scales(), request.rainColor());
    }

    /**
     * The drops of a kind, written as {@link PrecipitationRenderer#build} writes them, to upload and draw: those built
     * ahead, if they were built for this camera, partial tick and settings and not used yet; otherwise written now.
     * Good until the next call.
     *
     * @throws RuntimeException what the build threw, if it failed
     */
    public ParticleQuads quads(PrecipitationField field, Camera camera, float partialTick, boolean dustOnly,
                               List<Snowflakes.Flake> snowflakes, int largest, TextureAtlasSprite[] dust, DropScales scales,
                               RainColor rainColor) {
        var request = this.request;
        boolean taken = dustOnly ? this.dustTaken : this.dropsTaken;
        if (request == null || taken || !request.isFor(field, camera, partialTick, snowflakes, largest, dust, scales, rainColor)) {
            this.behind++;
            PrecipitationRenderer.build(field, this.now, PrecipitationRenderer.View.of(camera), partialTick, dustOnly,
                    snowflakes, largest, dust, scales, rainColor);
            return this.now;
        }

        this.join();
        this.ahead++;
        if (dustOnly) {
            this.dustTaken = true;
            return this.dust;
        }
        this.dropsTaken = true;
        return this.drops;
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

    @Override
    public void close() {
        this.discard();
        this.drops.close();
        this.dust.close();
        this.now.close();
    }
}
