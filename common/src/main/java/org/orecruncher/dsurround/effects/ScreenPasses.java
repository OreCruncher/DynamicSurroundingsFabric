// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;
import org.orecruncher.dsurround.Constants;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Passes over the whole of what they draw to (the ground fog's, the realistic aurora's): each a triangle made in the
 * vertex shader from the vertex's index, with no vertex buffer, and the targets at a share of the screen's size some
 * are drawn to. With {@link MeshDraw}, where effects meet the game's render passes. Render thread only.
 */
public final class ScreenPasses {

    private ScreenPasses() {
    }

    /**
     * The start of a screen pass's pipeline: its shaders, triangles, no culling. The caller adds what the pass binds,
     * its color targets and any depth state.
     *
     * @param location       the pipeline's id, under the mod's namespace
     * @param vertexShader   the vertex shader that makes the triangle, under the mod's namespace
     * @param fragmentShader the pass's fragment shader, under the mod's namespace
     */
    public static RenderPipeline.Builder pipeline(String location, String vertexShader, String fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId(vertexShader))
                .withFragmentShader(Constants.asId(fragmentShader))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
    }

    /**
     * Draws a screen pass to one color target, with no depth
     *
     * @param clear what the target is cleared to first; empty to draw over what is there
     * @param bind  binds what the pipeline reads: its uniform blocks and textures, and the defaults if it uses them
     */
    public static void draw(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear, RenderPipeline pipeline,
                            Consumer<RenderPass> bind) {
        draw(label, color, clear, pipeline, 3, bind);
    }

    /**
     * The same, with as many vertices as the vertex shader makes its triangles from (two triangles over part of the
     * screen, say), rather than the one triangle over all of it
     */
    public static void draw(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear, RenderPipeline pipeline,
                            int vertices, Consumer<RenderPass> bind) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(label, color, clear, null, OptionalDouble.empty())) {
            draw(pass, pipeline, vertices, bind);
        }
    }

    /**
     * Draws a screen pass as {@code descriptor} sets it out: for more than one color target, or a render area
     */
    public static void draw(RenderPassDescriptor descriptor, RenderPipeline pipeline, Consumer<RenderPass> bind) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
            draw(pass, pipeline, 3, bind);
        }
    }

    private static void draw(RenderPass pass, RenderPipeline pipeline, int vertices, Consumer<RenderPass> bind) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        bind.accept(pass);
        pass.draw(vertices, 1, 0, 0);
    }

    /**
     * A color target at a share of another's size, made on first use and resized to follow it
     */
    public static final class Reduced implements AutoCloseable {

        private final String label;
        @Nullable
        private TextureTarget target;

        /**
         * @param label what it holds, for the GPU's debugging labels
         */
        public Reduced(String label) {
            this.label = label;
        }

        /**
         * The target, at a pixel for each {@code scale} by {@code scale} of {@code full}'s, rounded up. Not while a
         * render pass is open: making or resizing it can't be done then.
         */
        public TextureTarget of(RenderTarget full, int scale) {
            int width = size(full.width, scale);
            int height = size(full.height, scale);
            if (this.target == null)
                this.target = new TextureTarget(this.label, width, height, GpuFormat.RGBA8_UNORM, null);
            else if (this.target.width != width || this.target.height != height)
                this.target.resize(width, height);
            return this.target;
        }

        /**
         * How many pixels across a share of {@code full} pixels is, rounded up: every pixel of the full size is under one
         */
        public static int size(int full, int scale) {
            return (full + scale - 1) / scale;
        }

        @Override
        public void close() {
            if (this.target != null) {
                this.target.destroyBuffers();
                this.target = null;
            }
        }
    }
}
