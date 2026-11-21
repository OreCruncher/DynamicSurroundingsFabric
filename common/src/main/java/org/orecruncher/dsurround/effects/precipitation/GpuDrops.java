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
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * The rain and snow with their quads made on the GPU ({@code weatherOptions.precipitation.gpuDrops}, on by default):
 * the drops packed once a tick ({@link PackedDrops}) are uploaded as they are into a texel buffer when they change, and
 * the vertex shader (shaders/core/precipitation_gpu.vsh) makes each drop's quad from its record, for the frame's camera
 * and partial tick, as {@link PrecipitationRenderer#build} does on the CPU. Nothing is built ahead or waited for, and
 * nothing is left out at the view's edges: the GPU clips what is off screen. The dust, snow flecks, window streaks and
 * damp spots are still built on the CPU. Render thread only.
 * <p>
 * A GPU need only allow 65,536 texels in a texel buffer, and the game can't ask how many more it allows; the drops at
 * the widest range need about 157,000. So they are drawn in batches of {@link #BATCH_DROPS}, each from a buffer of its
 * own: one draw at the default range, three at the widest. A buffer each, not parts of one: the game binds only a whole
 * buffer as a texel buffer ("Uniform texel buffers do not support a slice of a buffer").
 */
public final class GpuDrops implements AutoCloseable {

    /**
     * Drops drawn a batch, from a buffer of their own: five texels each, within the {@link #MAX_TEXELS} every GPU allows
     */
    static final int BATCH_DROPS = 12_800;
    static final int MAX_TEXELS = 65_536;
    static final long BATCH_BYTES = (long) BATCH_DROPS * PackedDrops.STRIDE * Float.BYTES;
    private static final int INFO_SIZE;

    static {
        var size = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4();
        // The snowflakes' rectangles, then their sizes four to a vec4
        for (int i = 0; i < Snowflakes.MAX_FLAKES + Snowflakes.MAX_FLAKES / 4; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    // A buffer for each batch, made as the drops need them, each of BATCH_BYTES
    private final List<GpuBuffer> batches = new ArrayList<>();
    @Nullable
    private GpuBuffer info;
    @Nullable
    private FloatBuffer staging;
    // Which pack the GPU's copy holds
    private long uploadedVersion = -1L;

    /**
     * Draws the rain and snow of {@code field}, as the CPU's quads would be drawn
     *
     * @param bind binds the snow texture and the lightmap, as for the CPU's quads
     * @return how many drops were drawn
     */
    public int draw(PrecipitationField field, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                    DropMeshes.Style style, Consumer<RenderPass> bind) {
        if (!PrecipitationRenderer.hasAny(field, false))
            return 0;
        var cam = camera.position();
        var packed = field.packed(cam.x, cam.y, cam.z);
        int drops;
        double anchorX, anchorY, anchorZ;
        // A late build of the dust may be packing the same drops on the quads' thread
        synchronized (packed) {
            drops = packed.solid;
            anchorX = packed.anchorX;
            anchorY = packed.anchorY;
            anchorZ = packed.anchorZ;
            if (drops == 0)
                return 0;
            this.upload(packed);
        }
        this.pack((float) (anchorX - cam.x), (float) (anchorY - cam.y), (float) (anchorZ - cam.z), partialTick,
                PrecipitationRenderer.View.of(camera), field.radius(), style);

        var device = RenderSystem.getDevice();
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(Math.min(drops, BATCH_DROPS) * 6);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        var info = this.info;
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Dynamic Surroundings rain and snow (GPU)",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            bind.accept(pass);
            pass.setUniform(PrecipitationShaders.DROP_INFO, info);
            pass.setIndexBuffer(indexBuffer, indices.type());
            // Each batch from its own buffer, its vertex index starting again at 0
            for (int first = 0, batch = 0; first < drops; first += BATCH_DROPS, batch++) {
                pass.setUniform(PrecipitationShaders.DROP_RECORDS, this.batches.get(batch));
                pass.drawIndexed(Math.min(BATCH_DROPS, drops - first) * 6, 1, 0, 0, 0);
            }
        }
        return drops;
    }

    // The rain and snow's records into the batches' buffers, if they changed
    private void upload(PackedDrops packed) {
        if (packed.version == this.uploadedVersion)
            return;
        int floats = packed.solid * PackedDrops.STRIDE;
        if (this.staging == null || this.staging.capacity() < floats) {
            int capacity = floats;
            if (this.staging != null) {
                capacity = Math.max(capacity, this.staging.capacity() * 3 / 2);
                MemoryUtil.memFree(this.staging);
            }
            this.staging = MemoryUtil.memAllocFloat(capacity);
        }
        var buffer = this.staging.clear();
        buffer.put(packed.data, 0, floats);
        buffer.flip();

        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var bytes = MemoryUtil.memByteBuffer(buffer);
        for (int batch = 0; (long) batch * BATCH_BYTES < bytes.remaining(); batch++) {
            if (batch == this.batches.size()) {
                int number = batch + 1;
                this.batches.add(device.createBuffer(() -> "Dynamic Surroundings drops " + number,
                        GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, BATCH_BYTES));
            }
            int start = (int) (batch * BATCH_BYTES);
            int length = (int) Math.min(BATCH_BYTES, bytes.remaining() - start);
            encoder.writeToBuffer(this.batches.get(batch).slice(0, length), bytes.slice(start, length));
        }
        this.uploadedVersion = packed.version;
    }

    // The frame's values, in DropInfo's order (see the shader)
    private void pack(float offsetX, float offsetY, float offsetZ, float partialTick, PrecipitationRenderer.View view,
                      float radius, DropMeshes.Style style) {
        if (this.info == null)
            this.info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings drops UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        float snowTurn = partialTick * PrecipitationField.SNOW_SWAY_RATE * PrecipitationRenderer.SNOW_SPIN;
        var left = view.left();
        var up = view.up();
        var rain = style.rainColor();
        var flakes = style.snowflakes();
        int count = Math.min(flakes.size(), Snowflakes.MAX_FLAKES);
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE)
                    .putVec4(offsetX, offsetY, offsetZ, partialTick)
                    .putVec4(left.x, left.y, left.z, radius)
                    .putVec4(up.x, up.y, up.z, style.scales().snow())
                    .putVec4(rain.red(), rain.green(), rain.blue(), style.scales().rain())
                    .putVec4(Mth.cos(snowTurn), Mth.sin(snowTurn), count, Math.max(1, style.largest()));
            for (int i = 0; i < Snowflakes.MAX_FLAKES; i++) {
                if (i < count) {
                    var flake = flakes.get(i);
                    builder.putVec4(flake.u0(), flake.v0(), flake.u1(), flake.v1());
                } else {
                    builder.putVec4(0F, 0F, 0F, 0F);
                }
            }
            for (int i = 0; i < Snowflakes.MAX_FLAKES; i += 4)
                builder.putVec4(size(flakes, count, i), size(flakes, count, i + 1), size(flakes, count, i + 2), size(flakes, count, i + 3));
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.info.slice(), builder.get());
        }
    }

    private static float size(List<Snowflakes.Flake> flakes, int count, int i) {
        return i < count ? flakes.get(i).size() : 0F;
    }

    @Override
    public void close() {
        for (var batch : this.batches)
            batch.close();
        this.batches.clear();
        if (this.info != null) {
            this.info.close();
            this.info = null;
        }
        if (this.staging != null) {
            MemoryUtil.memFree(this.staging);
            this.staging = null;
        }
        this.uploadedVersion = -1L;
    }
}
