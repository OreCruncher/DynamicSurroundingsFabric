// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.halo;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.Minecraft;
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

/**
 * Draws the sun's halo and sun dogs: a pass over the whole screen, after the sky, that takes each pixel's angle from
 * the sun (the halo, a ring at 22 degrees) and its place beside it (the sun dogs, level with the sun either side) and
 * adds their light, covering part of the sky behind it as the rainbow does. The world and the clouds, drawn after the
 * sky, cover it. Pixels further from the sun than anything drawn do no more than find that out. See
 * shaders/core/halo.fsh. Render thread only.
 */
public final class HaloRenderer {

    private static final String INFO = "HaloInfo";
    private static final int INFO_SIZE;
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .build();

    static {
        var size = new Std140SizeCalculator().putMat4f();
        for (int i = 0; i < HaloFrame.VECTORS; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    // Light added over the sky, covering part of it (the shader's alpha), as the rainbow's
    private static final BlendFunction OVER = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA);

    // The rainbow's vertex shader: a triangle over the whole screen, from the vertex's index alone. Nothing is tested
    // against or written to the depth buffer
    private static final RenderPipeline PIPELINE = ScreenPasses.pipeline("pipeline/halo", "core/rainbow", "core/halo")
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(OVER))
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the sun's halo", "halos will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Halo");

    // One buffer rewritten each frame, not a MappableRingBuffer (see the realistic aurora's)
    @Nullable
    private static GpuBuffer info;

    private HaloRenderer() {
    }

    /**
     * Whether a halo can be drawn: its pipeline compiled, and drawing hasn't failed since
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable();
    }

    /**
     * Draws the halo and sun dogs
     *
     * @param celestialAngle the sun's celestial angle in degrees (noon 0, midnight 180)
     * @param strength       how strongly the halo shows, 0 to 1
     * @param sunDogs        how strongly the sun dogs show, 0 to 1
     * @param viewMatrix     the camera's rotation, from the sky rendering
     * @param blocky         whether it is drawn in blocks, as the rainbow's Blocky style
     */
    public static void render(float celestialAngle, float strength, float sunDogs, Matrix4f viewMatrix, boolean blocky) {
        if (strength <= 0F || !isAvailable())
            return;
        RENDER_TIMER.begin();
        try {
            SHADER.run(() -> draw(celestialAngle, strength, sunDogs, viewMatrix, blocky), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(float celestialAngle, float strength, float sunDogs, Matrix4f viewMatrix, boolean blocky) {
        var frame = HaloFrame.of(celestialAngle, strength, sunDogs, LevelProjection.get(), viewMatrix, blocky);
        pack(frame);
        var block = info;
        var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        ScreenPasses.draw(() -> "Dynamic Surroundings halo", screen.getColorTextureView(), Optional.empty(), PIPELINE,
                pass -> pass.setUniform(INFO, block));
    }

    /**
     * Writes the frame's values into the uniform block (see shaders/include/halo.glsl)
     */
    private static void pack(HaloFrame frame) {
        if (info == null)
            info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings halo UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var v = frame.vec4s();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE).putMat4f(frame.invViewProj());
            for (int i = 0; i < v.length; i += 4)
                builder.putVec4(v[i], v[i + 1], v[i + 2], v[i + 3]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(), builder.get());
        }
    }
}
