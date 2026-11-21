// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * What the drops leave where they land, shaped on the GPU, as {@link GpuDrops} does the drops: the snow flecks, the
 * drops on windows and the damp spots, packed once a tick ({@link PackedMarks}) and shaped by
 * shaders/core/precipitation_marks.vsh for the frame's camera and partial tick, as {@link PrecipitationRenderer}'s builds
 * do on the CPU. One of these for each kind, each drawn in a pass of its own as before, with its own records and uniform
 * block. Render thread only.
 */
public final class GpuMarks implements AutoCloseable {

    /**
     * Marks drawn a batch, from a buffer of their own: three texels each, within the 65,536 every GPU allows
     */
    static final int BATCH_MARKS = 16_384;
    private static final int INFO_SIZE;

    static {
        var size = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4();
        // The snowflakes' rectangles, then their sizes four to a vec4
        for (int i = 0; i < GpuDrops.MAX_LOOKS + GpuDrops.MAX_LOOKS / 4; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    /**
     * Which marks one draws, the shader's number for them, and how many quads each takes
     */
    public enum Kind {
        FLECKS("snow flecks", 1, 1),
        STREAKS("window streaks", 2, 2),
        SPOTS("damp spots", 3, 1);

        final String description;
        final int id;
        final int quads;

        Kind(String description, int id, int quads) {
            this.description = description;
            this.id = id;
            this.quads = quads;
        }
    }

    private final Kind kind;
    private final TexelBatches records;
    @Nullable
    private GpuBuffer info;
    // Which pack the GPU's copy holds
    private long uploadedVersion = -1L;

    public GpuMarks(Kind kind) {
        this.kind = kind;
        this.records = new TexelBatches(kind.description, PackedMarks.STRIDE, BATCH_MARKS);
    }

    /**
     * Draws the snow flecks (a {@link Kind#FLECKS} one), as the CPU's quads would be drawn
     *
     * @param bind binds the snow texture and the lightmap, as for the CPU's quads
     * @return how many were drawn
     */
    public int draw(SnowFlecks flecks, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                    DropMeshes.Style style, Consumer<RenderPass> bind) {
        var cam = camera.position();
        return this.draw(flecks.packed(cam.x, cam.y, cam.z), pipeline, target, camera, partialTick, style, bind);
    }

    /**
     * Draws the drops on windows (a {@link Kind#STREAKS} one), as the CPU's quads would be drawn
     *
     * @param bind binds the snow texture and the lightmap, as for the CPU's quads
     * @return how many were drawn
     */
    public int draw(WindowStreaks streaks, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                    DropMeshes.Style style, Consumer<RenderPass> bind) {
        var cam = camera.position();
        return this.draw(streaks.packed(cam.x, cam.y, cam.z), pipeline, target, camera, partialTick, style, bind);
    }

    /**
     * Draws the damp spots (a {@link Kind#SPOTS} one), as the CPU's quads would be drawn
     *
     * @param bind binds the particle atlas, as for the CPU's quads
     * @return how many were drawn
     */
    public int draw(DampSpots spots, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                    DropMeshes.Style style, Consumer<RenderPass> bind) {
        var cam = camera.position();
        return this.draw(spots.packed(cam.x, cam.y, cam.z), pipeline, target, camera, partialTick, style, bind);
    }

    private int draw(PackedMarks packed, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                     DropMeshes.Style style, Consumer<RenderPass> bind) {
        int marks;
        double anchorX, anchorY, anchorZ;
        // The quads' thread may be packing the same marks
        synchronized (packed) {
            marks = packed.count;
            anchorX = packed.anchorX;
            anchorY = packed.anchorY;
            anchorZ = packed.anchorZ;
            if (marks == 0)
                return 0;
            if (packed.version != this.uploadedVersion) {
                this.records.upload(packed.data, 0, marks);
                this.uploadedVersion = packed.version;
            }
        }
        var cam = camera.position();
        this.pack((float) (anchorX - cam.x), (float) (anchorY - cam.y), (float) (anchorZ - cam.z), partialTick, style);

        var device = RenderSystem.getDevice();
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(Math.min(marks, BATCH_MARKS) * this.kind.quads * 6);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        var info = this.info;
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Dynamic Surroundings " + this.kind.description + " (GPU)",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            bind.accept(pass);
            pass.setUniform(PrecipitationShaders.MARK_INFO, info);
            pass.setIndexBuffer(indexBuffer, indices.type());
            // Each batch from its own buffer, its vertex index starting again at 0
            for (int first = 0, batch = 0; first < marks; first += BATCH_MARKS, batch++) {
                pass.setUniform(PrecipitationShaders.MARK_RECORDS, this.records.batch(batch));
                pass.drawIndexed(Math.min(BATCH_MARKS, marks - first) * this.kind.quads * 6, 1, 0, 0, 0);
            }
        }
        return marks;
    }

    // The frame's values, in MarkInfo's order (see the shader)
    private void pack(float offsetX, float offsetY, float offsetZ, float partialTick, DropMeshes.Style style) {
        if (this.info == null)
            this.info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings " + this.kind.description + " UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var rain = style.rainColor();
        List<Snowflakes.Flake> flakes = style.snowflakes();
        int count = Math.min(flakes.size(), GpuDrops.MAX_LOOKS);
        TextureAtlasSprite spot = style.spot();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE)
                    .putVec4(offsetX, offsetY, offsetZ, partialTick)
                    .putVec4(rain.red(), rain.green(), rain.blue(), this.kind.id)
                    .putVec4(style.scales().snow(), count, Math.max(1, style.largest()), 0F);
            if (spot != null)
                builder.putVec4(spot.getU0(), spot.getV0(), spot.getU1(), spot.getV1());
            else
                builder.putVec4(0F, 0F, 0F, 0F);
            for (int i = 0; i < GpuDrops.MAX_LOOKS; i++) {
                if (i < count) {
                    var flake = flakes.get(i);
                    builder.putVec4(flake.u0(), flake.v0(), flake.u1(), flake.v1());
                } else {
                    builder.putVec4(0F, 0F, 0F, 0F);
                }
            }
            for (int i = 0; i < GpuDrops.MAX_LOOKS; i += 4)
                builder.putVec4(size(flakes, count, i), size(flakes, count, i + 1), size(flakes, count, i + 2), size(flakes, count, i + 3));
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.info.slice(), builder.get());
        }
    }

    private static float size(List<Snowflakes.Flake> flakes, int count, int i) {
        return i < count ? flakes.get(i).size() : 0F;
    }

    @Override
    public void close() {
        this.records.close();
        if (this.info != null) {
            this.info.close();
            this.info = null;
        }
        this.uploadedVersion = -1L;
    }
}
