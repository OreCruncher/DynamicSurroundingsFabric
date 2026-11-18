// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AuroraQuality;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;

/**
 * The realistic aurora as it was drawn each frame, for the recording /dsprofile is making (see
 * {@link RealisticAuroraEvent}): what the night made of it, how finely it was drawn, and how many pixels the shader
 * marched, which its GPU time follows (it works every pixel looking above the horizon, the sky behind the world
 * included). While nothing records, the renderer doesn't work those pixels out. Uses nothing from {@code jdk.jfr}, so
 * the renderer can call it in a Java without that module.
 */
public final class RealisticAuroraSamples {

    @FunctionalInterface
    interface Sink {
        void drawn(RealisticAurora aurora, RealisticAurora.Moment moment, AuroraQuality quality, float strength,
                   int width, int height, double marchedShare);
    }

    private static volatile @Nullable Sink sink;

    private RealisticAuroraSamples() {
    }

    /**
     * Whether a recording wants the aurora's frames; asked once a frame
     */
    public static boolean isRecording() {
        return sink != null;
    }

    /**
     * One frame's aurora, from the render thread.
     *
     * @param strength     how strongly it was drawn, 0 to 1
     * @param width        the width in pixels of what it was drawn to (half the screen's at Fast)
     * @param height       its height
     * @param marchedShare the share of those pixels the shader marched, 0 to 1 (see
     *                     {@code RealisticAuroraFrame.marchedShare})
     */
    public static void drawn(RealisticAurora aurora, RealisticAurora.Moment moment, AuroraQuality quality, float strength,
                             int width, int height, double marchedShare) {
        var current = sink;
        if (current != null)
            current.drawn(aurora, moment, quality, strength, width, height, marchedShare);
    }

    static void start(Sink recording) {
        sink = recording;
    }

    static void stop() {
        sink = null;
    }
}
