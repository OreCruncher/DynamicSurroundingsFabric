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
import org.joml.Vector3f;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The weather's quads, built ahead on another thread: as the level starts to be drawn ({@link #begin}), once the camera
 * is set for the frame, so they are built while the render thread draws the terrain, and the weather's pass only draws
 * them ({@link #draw}). Building them, every drop facing the camera and placed between its ticks, was the largest part
 * of the weather's time on the render thread.
 * <p>
 * Built for the camera and partial tick as the level began; if the weather is drawn for another (a mod drawing the level
 * again from elsewhere: a portal, a mirror), or drawn twice, or their build isn't done after a short wait
 * ({@link #await}), they are built there and then, on the render thread, as before. Each {@link Kind} is built into a
 * buffer of its own and drawn at most once.
 * <p>
 * The build reads the drops, and what they left where they landed, which only the client tick changes, when it
 * finishes the drops' step and lands them; the tick waits for a build under way, and drops it, first
 * ({@link #discard}). Render thread only, but for the build itself.
 */
public final class DropMeshes {

    // Room to start with, in bytes, for each kind: a few thousand drops, before the buffer grows
    private static final int INITIAL_BYTES = 256 * 1024;
    // How long the weather's pass waits for a build under way ({@link #await}): about what building every kind on the
    // render thread takes, so waiting longer doesn't pay. With the machine busy (90% of the CPU and more, the game
    // catching up), the build's thread wasn't run for 10 to 30 ms while the render thread waited.
    static final long WAIT_NANOS = 2_000_000L;

    /**
     * What is drawn, each in a pass of its own
     */
    public enum Kind {
        DROPS("the rain and snow", DefaultVertexFormat.PARTICLE),
        DUST("the dust", DefaultVertexFormat.PARTICLE),
        FLECKS("the snow flecks", DefaultVertexFormat.PARTICLE),
        STREAKS("the window streaks", DefaultVertexFormat.PARTICLE),
        SPOTS("the damp spots", DefaultVertexFormat.POSITION_TEX_COLOR);

        final String description;
        final VertexFormat format;

        Kind(String description, VertexFormat format) {
            this.description = description;
            this.format = format;
        }
    }

    /**
     * What the quads are built from: the drops, and what they left where they landed
     */
    public record Sources(PrecipitationField field, SnowFlecks flecks, WindowStreaks streaks, DampSpots spots) {

        boolean hasAny(Kind kind) {
            return switch (kind) {
                case DROPS -> PrecipitationRenderer.hasAny(this.field, false);
                case DUST -> PrecipitationRenderer.hasAny(this.field, true);
                case FLECKS -> this.flecks.count() > 0;
                case STREAKS -> this.streaks.count() > 0;
                case SPOTS -> this.spots.count() > 0;
            };
        }

        /**
         * Whether there is nothing of any kind to draw
         */
        public boolean isEmpty() {
            return this.field.count() == 0 && this.flecks.count() == 0 && this.streaks.count() == 0 && this.spots.count() == 0;
        }
    }

    /**
     * How the quads are drawn
     *
     * @param snowflakes the flakes of the snow texture; empty to draw plain flakes
     * @param largest    the largest of them, in pixels: the others are drawn in proportion
     * @param dust       the dust sprites, from the particle atlas
     * @param scales     how big rain and snow are drawn
     * @param rainColor  the rain's color, the drops on windows' too
     * @param spot       the damp spots' sprite, from the particle atlas
     * @param darkening  true when the damp spots darken the ground by their alpha; false to lay a dark color over it
     */
    public record Style(List<Snowflakes.Flake> snowflakes, int largest, TextureAtlasSprite[] dust, DropScales scales,
                        RainColor rainColor, TextureAtlasSprite spot, boolean darkening) {

        List<String> differences(Style other) {
            var changed = new ArrayList<String>();
            if (this.snowflakes != other.snowflakes)
                changed.add("snowflakes");
            if (this.largest != other.largest)
                changed.add("largest flake %d to %d".formatted(this.largest, other.largest));
            if (this.dust != other.dust)
                changed.add("dust sprites");
            if (!this.scales.equals(other.scales))
                changed.add("scales %s to %s".formatted(this.scales, other.scales));
            if (!this.rainColor.equals(other.rainColor))
                changed.add("rain color %s to %s".formatted(this.rainColor, other.rainColor));
            if (this.spot != other.spot)
                changed.add("damp spot sprite");
            if (this.darkening != other.darkening)
                changed.add("damp spots darkening %s to %s".formatted(this.darkening, other.darkening));
            return changed;
        }
    }

    /**
     * Why quads built ahead weren't drawn, and they were built on the render thread instead
     */
    enum Behind {
        NOT_BUILT("nothing was built ahead for this frame"),
        DRAWN_TWICE("the weather was drawn a second time this frame"),
        OTHER_SOURCES("they were built from other drops, or other things they left"),
        CAMERA_MOVED("the camera moved after the level began to be drawn"),
        CAMERA_TURNED("the camera turned after the level began to be drawn"),
        PARTIAL_TICK("the partial tick changed after the level began to be drawn"),
        SETTINGS("the drawing settings changed"),
        // Not logged: a busy machine does it now and then (see WAIT_NANOS)
        LATE("their build wasn't done in time");

        final String reason;

        Behind(String reason) {
            this.reason = reason;
        }
    }

    /**
     * Why quads built ahead weren't drawn, with what differed
     */
    record Mismatch(Behind kind, String detail) {
    }

    /**
     * What a frame's quads were built for: drawn only for the same
     */
    record Request(Sources sources, PrecipitationRenderer.View view, float partialTick, Style style) {

        /**
         * What differs in {@code now} from what these were built for, the first difference found; null if they are for
         * it
         */
        @Nullable
        Mismatch mismatch(Request now) {
            if (!this.sources.equals(now.sources))
                return new Mismatch(Behind.OTHER_SOURCES, replaced(this.sources, now.sources));
            if (!this.view.position().equals(now.view.position()))
                return new Mismatch(Behind.CAMERA_MOVED, "from %s to %s".formatted(this.view.position(), now.view.position()));
            if (!this.view.left().equals(now.view.left()) || !this.view.up().equals(now.view.up()) || !this.view.look().equals(now.view.look()))
                return new Mismatch(Behind.CAMERA_TURNED, "look %s to %s, up %s to %s, left %s to %s".formatted(
                        text(this.view.look()), text(now.view.look()), text(this.view.up()), text(now.view.up()),
                        text(this.view.left()), text(now.view.left())));
            if (this.partialTick != now.partialTick)
                return new Mismatch(Behind.PARTIAL_TICK, "%s to %s".formatted(this.partialTick, now.partialTick));
            var changed = this.style.differences(now.style);
            return changed.isEmpty() ? null : new Mismatch(Behind.SETTINGS, String.join(", ", changed));
        }

        private static String replaced(Sources was, Sources now) {
            var replaced = new ArrayList<String>();
            if (was.field() != now.field())
                replaced.add("the field");
            if (was.flecks() != now.flecks())
                replaced.add("the snow flecks");
            if (was.streaks() != now.streaks())
                replaced.add("the window streaks");
            if (was.spots() != now.spots())
                replaced.add("the damp spots");
            return String.join(", ", replaced) + " replaced";
        }

        // Every digit, so a difference of rounding shows
        private static String text(Vector3f v) {
            return "(" + v.x + ", " + v.y + ", " + v.z + ")";
        }
    }

    private static final Kind[] KINDS = Kind.values();

    private final IModLog logger;
    // What has been logged, once each, for a session
    private final EnumSet<Behind> reported = EnumSet.noneOf(Behind.class);
    private final ByteBufferBuilder[] bytes = new ByteBufferBuilder[KINDS.length];
    // Set by the build, read once it is joined; null once drawn, or if there were none
    private final MeshData[] meshes = new MeshData[KINDS.length];
    private final boolean[] taken = new boolean[KINDS.length];
    @Nullable
    private Request request;
    @Nullable
    private CompletableFuture<Void> pending;
    // Kinds drawn from what was built ahead, and built on the render thread instead, since the diagnostics last asked
    private int ahead;
    private int behind;

    public DropMeshes(IModLog logger) {
        this.logger = logger;
        for (int i = 0; i < KINDS.length; i++)
            this.bytes[i] = new ByteBufferBuilder(INITIAL_BYTES);
    }

    /**
     * Starts building the frame's quads on {@code executor}, for the camera as it is now. What was built for the frame
     * before and not drawn is dropped.
     */
    public void begin(Sources sources, Camera camera, float partialTick, Style style, Executor executor) {
        this.discard();
        if (sources.isEmpty())
            return;
        var request = new Request(sources, PrecipitationRenderer.View.of(camera), partialTick, style);
        this.request = request;
        this.pending = CompletableFuture.runAsync(() -> this.build(request), executor);
    }

    // On the executor's thread
    private void build(Request request) {
        for (var kind : KINDS) {
            if (request.sources().hasAny(kind)) {
                var builder = new BufferBuilder(this.bytes[kind.ordinal()], VertexFormat.Mode.QUADS, kind.format);
                build(kind, builder, request);
                this.meshes[kind.ordinal()] = builder.build();
            }
        }
    }

    /**
     * Writes the quads of a kind for a request, as {@link PrecipitationRenderer#build} does
     */
    static void build(Kind kind, BufferBuilder builder, Request request) {
        var sources = request.sources();
        var style = request.style();
        var cam = request.view().position();
        float partialTick = request.partialTick();
        switch (kind) {
            case DROPS, DUST -> PrecipitationRenderer.build(sources.field(), builder, request.view(), partialTick,
                    kind == Kind.DUST, style.snowflakes(), style.largest(), style.dust(), style.scales(), style.rainColor());
            case FLECKS -> PrecipitationRenderer.build(sources.flecks(), builder, cam, partialTick, style.snowflakes(),
                    style.largest(), style.scales().snow());
            case STREAKS -> PrecipitationRenderer.build(sources.streaks(), builder, cam, partialTick, style.rainColor());
            case SPOTS -> PrecipitationRenderer.build(sources.spots(), builder, cam, partialTick, style.spot(), style.darkening());
        }
    }

    /**
     * Draws the quads of a kind with whatever shader is current, which should take the kind's vertex format, with what
     * it needs bound (see {@link PrecipitationRenderer}): those built ahead, if they were built for this camera, partial
     * tick and settings and not drawn yet; otherwise built now
     *
     * @return how many quads were drawn
     * @throws RuntimeException what the build threw, if it failed
     */
    public int draw(Kind kind, Sources sources, Tesselator tesselator, Camera camera, float partialTick, Style style) {
        if (!sources.hasAny(kind))
            return 0;
        var now = new Request(sources, PrecipitationRenderer.View.of(camera), partialTick, style);
        var mismatch = this.mismatch(kind, now);
        if (mismatch != null) {
            this.behind++;
            this.report(mismatch);
            // What the build makes of it later isn't drawn
            if (mismatch.kind() == Behind.LATE)
                this.taken[kind.ordinal()] = true;
            var builder = tesselator.begin(VertexFormat.Mode.QUADS, kind.format);
            build(kind, builder, now);
            return draw(builder.build());
        }

        this.join();
        this.ahead++;
        var mesh = this.meshes[kind.ordinal()];
        this.meshes[kind.ordinal()] = null;
        this.taken[kind.ordinal()] = true;
        return draw(mesh);
    }

    /**
     * Why a kind can't be drawn from what was built ahead for the frame, or null if it can
     */
    @Nullable
    Mismatch mismatch(Kind kind, Request now) {
        var request = this.request;
        if (request == null)
            return new Mismatch(Behind.NOT_BUILT, kind.description);
        if (this.taken[kind.ordinal()])
            return new Mismatch(Behind.DRAWN_TWICE, kind.description);
        var mismatch = request.mismatch(now);
        if (mismatch != null)
            return mismatch;
        var running = this.pending;
        return running != null && !running.isDone() ? new Mismatch(Behind.LATE, kind.description) : null;
    }

    /**
     * Waits for the build under way to finish, if there is one, but no longer than {@link #WAIT_NANOS}: once a frame,
     * before the first kind is drawn. Kinds drawn while it is still under way are built on the render thread instead
     * of waiting for it, a starved build costing the frame no more than building them would.
     */
    public void await() {
        var running = this.pending;
        if (running == null || running.isDone())
            return;
        try {
            running.get(WAIT_NANOS, TimeUnit.NANOSECONDS);
        } catch (TimeoutException ignored) {
            // Late: built on the render thread as they are drawn
        } catch (ExecutionException ignored) {
            // Thrown when drawn, from join
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int draw(@Nullable MeshData mesh) {
        if (mesh == null)
            return 0;
        int drawn = mesh.drawState().vertexCount() / 4;
        BufferUploader.drawWithShader(mesh);
        return drawn;
    }

    /**
     * Logs why quads built ahead weren't drawn, once for each reason a session. A mod drawing the level again (a
     * portal, a mirror) does this now and then; every frame, the render thread builds them all, as if never built
     * ahead. The diagnostics' Precipitation line says how often ("built ahead").
     */
    void report(Mismatch mismatch) {
        if (mismatch.kind() != Behind.LATE && this.reported.add(mismatch.kind()))
            this.logger.info("Rain and snow built ahead were built again on the render thread: %s (%s). If the diagnostics show them seldom built ahead, please report this along with your mod list.",
                    mismatch.kind().reason, mismatch.detail());
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
     * Waits for a build under way and drops what was built and not drawn: before what it was built from changes, and
     * before the next frame's
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
        for (int i = 0; i < KINDS.length; i++) {
            if (this.meshes[i] != null) {
                this.meshes[i].close();
                this.meshes[i] = null;
            }
            this.taken[i] = false;
        }
        this.request = null;
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
