// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.RenderTimer;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * How long our particle layers take to draw, each a draw of its own inside the game's particle drawing
 * (QuadParticleFeatureRenderer.drawLayers): begun as a layer's pipeline is chosen, ended once it is drawn. Our particles
 * drawn in vanilla's layers (foam, ripples) are drawn with vanilla's and can't be timed apart; their CPU time is in the
 * recordings' particle counts (see {@code ParticleSamples}). With Improved Transparency on, each layer is drawn once in
 * each of its stages, with the stages' pipelines, and isn't timed. Render thread only.
 */
public final class ParticleLayerTimers {

    public static final RenderTimer MIST = new RenderTimer("Particles: Mist");
    public static final RenderTimer FLAMES = new RenderTimer("Particles: Flames");
    public static final RenderTimer EYE_FLAMES = new RenderTimer("Particles: Eye Flames");
    public static final RenderTimer FIREFLIES = new RenderTimer("Particles: Fireflies");

    private static final Map<RenderPipeline, RenderTimer> BY_PIPELINE = new IdentityHashMap<>();
    @Nullable
    private static RenderTimer active;

    private ParticleLayerTimers() {
    }

    /**
     * Says which timer times the layers drawn with a pipeline, from the class that makes it
     */
    static synchronized void register(RenderPipeline pipeline, RenderTimer timer) {
        BY_PIPELINE.put(pipeline, timer);
    }

    /**
     * A layer's drawing begins, with the pipeline its layer gives (before any fallback is chosen for it)
     */
    public static void begin(RenderPipeline pipeline) {
        end();
        var timer = BY_PIPELINE.get(pipeline);
        if (timer != null) {
            timer.begin();
            active = timer;
        }
    }

    /**
     * The layer's drawing is done
     */
    public static void end() {
        var timer = active;
        if (timer != null) {
            active = null;
            timer.end();
        }
    }
}
