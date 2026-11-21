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

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Rain and snow, or dust, with their quads made on the GPU ({@code weatherOptions.precipitation.gpuDrops}, on by
 * default): the drops packed once a tick ({@link PackedDrops}) are uploaded as they are into texel buffers when they
 * change, and the vertex shader (shaders/core/precipitation_gpu.vsh) makes each drop's quad from its record, for the
 * frame's camera and partial tick, as {@link PrecipitationRenderer#build} does on the CPU. Nothing is built ahead or
 * waited for, and nothing is left out at the view's edges: the GPU clips what is off screen. One of these draws the
 * rain and snow ({@link Part#DROPS}), another the dust ({@link Part#DUST}), each with its own records and uniform block, as the
 * two are drawn in passes of their own (the dust soft, from the particle atlas). The snow flecks, window streaks and
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
    static final int MAX_TEXELS = TexelBatches.MAX_TEXELS;
    static final long BATCH_BYTES = (long) BATCH_DROPS * PackedDrops.STRIDE * Float.BYTES;
    // As many looks as there may be snowflakes: the dust has fewer sprites
    static final int MAX_LOOKS = Snowflakes.MAX_FLAKES;
    private static final int INFO_SIZE;

    static {
        var size = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4();
        // The looks' rectangles, then the snowflakes' sizes four to a vec4
        for (int i = 0; i < MAX_LOOKS + MAX_LOOKS / 4; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    /**
     * Which drops one draws: the rain and snow, or the dust
     */
    public enum Part {
        DROPS("rain and snow"),
        DUST("dust");

        final String description;

        Part(String description) {
            this.description = description;
        }
    }

    private final Part part;
    // The records, a buffer for each batch
    private final TexelBatches records;
    @Nullable
    private GpuBuffer info;
    // Which pack the GPU's copy holds
    private long uploadedVersion = -1L;

    public GpuDrops(Part part) {
        this.part = part;
        this.records = new TexelBatches(part.description, PackedDrops.STRIDE, BATCH_DROPS);
    }

    /**
     * Draws this one's part of {@code field}, as the CPU's quads would be drawn
     *
     * @param bind binds what the CPU's quads are drawn with: the snow texture or the particle atlas, the lightmap, and
     *             the soft dust's depth
     * @return how many drops were drawn
     */
    public int draw(PrecipitationField field, RenderPipeline pipeline, RenderTarget target, Camera camera, float partialTick,
                    DropMeshes.Style style, Consumer<RenderPass> bind) {
        boolean dust = this.part == Part.DUST;
        if (!PrecipitationRenderer.hasAny(field, dust))
            return 0;
        var cam = camera.position();
        var packed = field.packed(cam.x, cam.y, cam.z);
        int drops;
        double anchorX, anchorY, anchorZ;
        // A late build on the quads' thread may be packing the same drops
        synchronized (packed) {
            drops = dust ? packed.dust : packed.solid;
            anchorX = packed.anchorX;
            anchorY = packed.anchorY;
            anchorZ = packed.anchorZ;
            if (drops == 0)
                return 0;
            this.upload(packed, dust ? packed.solid : 0, drops);
        }
        this.pack((float) (anchorX - cam.x), (float) (anchorY - cam.y), (float) (anchorZ - cam.z), partialTick,
                PrecipitationRenderer.View.of(camera), field.radius(), style);

        var device = RenderSystem.getDevice();
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(Math.min(drops, BATCH_DROPS) * 6);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        var info = this.info;
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Dynamic Surroundings " + this.part.description + " (GPU)",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            bind.accept(pass);
            pass.setUniform(PrecipitationShaders.DROP_INFO, info);
            pass.setIndexBuffer(indexBuffer, indices.type());
            // Each batch from its own buffer, its vertex index starting again at 0
            for (int first = 0, batch = 0; first < drops; first += BATCH_DROPS, batch++) {
                pass.setUniform(PrecipitationShaders.DROP_RECORDS, this.records.batch(batch));
                pass.drawIndexed(Math.min(BATCH_DROPS, drops - first) * 6, 1, 0, 0, 0);
            }
        }
        return drops;
    }

    // The part's records, from record {@code first}, into the batches' buffers, if they changed
    private void upload(PackedDrops packed, int first, int count) {
        if (packed.version == this.uploadedVersion)
            return;
        this.records.upload(packed.data, first, count);
        this.uploadedVersion = packed.version;
    }

    // The frame's values, in DropInfo's order (see the shader)
    private void pack(float offsetX, float offsetY, float offsetZ, float partialTick, PrecipitationRenderer.View view,
                      float radius, DropMeshes.Style style) {
        if (this.info == null)
            this.info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings " + this.part.description + " UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        boolean dust = this.part == Part.DUST;
        // How far every flake or puff has turned since its last tick (see PrecipitationRenderer.build)
        float turn = dust ? partialTick * PrecipitationField.DUST_TURBULENCE_RATE * PrecipitationRenderer.DUST_SPIN
                : partialTick * PrecipitationField.SNOW_SWAY_RATE * PrecipitationRenderer.SNOW_SPIN;
        var left = view.left();
        var up = view.up();
        var rain = style.rainColor();
        var flakes = style.snowflakes();
        var sprites = style.dust();
        int count = Math.min(dust ? sprites.length : flakes.size(), MAX_LOOKS);
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE)
                    .putVec4(offsetX, offsetY, offsetZ, partialTick)
                    .putVec4(left.x, left.y, left.z, radius)
                    .putVec4(up.x, up.y, up.z, style.scales().snow())
                    .putVec4(rain.red(), rain.green(), rain.blue(), style.scales().rain())
                    .putVec4(Mth.cos(turn), Mth.sin(turn), count, Math.max(1, style.largest()));
            for (int i = 0; i < MAX_LOOKS; i++) {
                if (i >= count) {
                    builder.putVec4(0F, 0F, 0F, 0F);
                } else if (dust) {
                    var sprite = sprites[i];
                    builder.putVec4(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
                } else {
                    var flake = flakes.get(i);
                    builder.putVec4(flake.u0(), flake.v0(), flake.u1(), flake.v1());
                }
            }
            int flakeCount = dust ? 0 : count;
            for (int i = 0; i < MAX_LOOKS; i += 4)
                builder.putVec4(size(flakes, flakeCount, i), size(flakes, flakeCount, i + 1), size(flakes, flakeCount, i + 2),
                        size(flakes, flakeCount, i + 3));
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
