// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.rainbow;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.effects.LevelProjection;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.ScreenPasses;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Draws a rainbow: a pass over the whole screen, after the sky, that takes each pixel's angle from the point opposite
 * the sun and adds the light the rain sends back at that angle ({@link RainbowSpectrum}, a texture of two rows: the
 * primary bow and the secondary). The light adds to the sky behind it and covers part of it, so the bow shows
 * strongly against a dark storm and keeps its colors against a bright sky; the world and the clouds, drawn after the
 * sky, cover it. Pixels
 * further from the point opposite the sun than the secondary bow do no more than find that out. See
 * shaders/core/rainbow.fsh. Render thread only.
 */
public final class RainbowRenderer {

    private static final String INFO = "RainbowInfo";
    private static final int INFO_SIZE;
    private static final String SPECTRUM_SAMPLER = "SpectrumSampler";
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(SPECTRUM_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static {
        var size = new Std140SizeCalculator().putMat4f();
        for (int i = 0; i < RainbowFrame.VECTORS; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    // Light added over the sky, covering part of it (the shader's alpha): added alone, the bow came out nearly white
    // on a bright sky
    private static final BlendFunction OVER = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA);

    // A triangle over the whole screen, from the vertex's index alone. Nothing is tested against or written to the
    // depth buffer
    private static final RenderPipeline PIPELINE = ScreenPasses.pipeline("pipeline/rainbow", "core/rainbow", "core/rainbow")
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(OVER))
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the rainbow", "rainbows will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Rainbow");

    // Worked out on another thread the first time it's asked for: it takes a moment
    @Nullable
    private static CompletableFuture<RainbowSpectrum> spectrum;
    @Nullable
    private static DynamicTexture texture;
    // One buffer rewritten each frame, not a MappableRingBuffer (see the realistic aurora's)
    @Nullable
    private static GpuBuffer info;

    private RainbowRenderer() {
    }

    /**
     * Starts working out the rainbow's colors, if they aren't yet, so they are ready by the first rainbow
     */
    public static void prepare() {
        if (spectrum == null)
            spectrum = CompletableFuture.supplyAsync(RainbowSpectrum::create);
    }

    /**
     * Whether a rainbow can be drawn: its pipeline compiled, and drawing hasn't failed since
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable();
    }

    /**
     * Draws the rainbow
     *
     * @param celestialAngle the sun's celestial angle in degrees (noon 0, midnight 180)
     * @param strength       how strongly, 0 to 1
     * @param secondary      how strongly the secondary bow shows, 0 to 1
     * @param viewMatrix     the camera's rotation, from the sky rendering
     * @param time           seconds, for the drifting of its patches
     * @param seed           where its patches are
     * @param blocky         whether it is drawn as flat stripes in blocks rather than its real colors
     * @param moonbow        whether it is a moonbow, in moonlight opposite the moon
     */
    public static void render(float celestialAngle, float strength, float secondary, Matrix4f viewMatrix, float time, float seed,
                              boolean blocky, boolean moonbow) {
        if (strength <= 0F || !isAvailable())
            return;
        prepare();
        var colors = spectrum;
        if (!colors.isDone())
            return;
        RENDER_TIMER.begin();
        try {
            // join rethrows a spectrum that failed to be made: the fail-safe logs it once and turns the rainbow off
            SHADER.run(() -> draw(colors.join(), celestialAngle, strength, secondary, viewMatrix, time, seed, blocky, moonbow), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(RainbowSpectrum colors, float celestialAngle, float strength, float secondary, Matrix4f viewMatrix,
                             float time, float seed, boolean blocky, boolean moonbow) {
        var frame = RainbowFrame.of(celestialAngle, strength, secondary, LevelProjection.get(), viewMatrix, time, seed, blocky,
                moonbow);
        pack(frame);
        // Made the first time it is used, which can't be while a render pass is open
        var view = texture(colors).getTextureView();
        var block = info;
        var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        ScreenPasses.draw(() -> "Dynamic Surroundings rainbow", screen.getColorTextureView(), Optional.empty(), PIPELINE, pass -> {
            pass.setUniform(INFO, block);
            pass.setUniform(SPECTRUM_SAMPLER, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        });
    }

    /**
     * Writes the frame's values into the uniform block (see shaders/include/rainbow.glsl)
     */
    private static void pack(RainbowFrame frame) {
        if (info == null)
            info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings rainbow UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var v = frame.vec4s();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE).putMat4f(frame.invViewProj());
            for (int i = 0; i < v.length; i += 4)
                builder.putVec4(v[i], v[i + 1], v[i + 2], v[i + 3]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(), builder.get());
        }
    }

    // The colors as a texture: a row for each bow, the square root of each channel for more precision in its faint
    // parts (the shader squares them)
    private static DynamicTexture texture(RainbowSpectrum colors) {
        if (texture == null) {
            var pixels = new NativeImage(RainbowSpectrum.SIZE, 2, false);
            writeRow(pixels, 0, colors.primary());
            writeRow(pixels, 1, colors.secondary());
            texture = new DynamicTexture(() -> "Dynamic Surroundings rainbow colors", pixels);
        }
        return texture;
    }

    private static void writeRow(NativeImage pixels, int row, float[] rgb) {
        for (int i = 0; i < RainbowSpectrum.SIZE; i++) {
            int r = channel(rgb[i * 3]);
            int g = channel(rgb[i * 3 + 1]);
            int b = channel(rgb[i * 3 + 2]);
            pixels.setPixelABGR(i, row, 0xFF000000 | b << 16 | g << 8 | r);
        }
    }

    static int channel(float linear) {
        return Math.clamp(Math.round((float) Math.sqrt(Math.max(linear, 0F)) * 255F), 0, 255);
    }
}
