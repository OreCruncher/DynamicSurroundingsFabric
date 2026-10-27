package org.orecruncher.dsurround.profiling;

/**
 * A recording's headline, for chat as it finishes (see {@link ProfileSummary#headline}): the frame rate and the effect
 * that cost the largest share of drawing a frame. Uses nothing from {@code jdk.jfr}, so {@link Profiler} can hold one.
 *
 * @param fps      frames a second
 * @param effect   the costliest effect, as its render timer names it
 * @param gpuShare its share of the GPU's time drawing a frame, 0 to 1
 * @param cpuShare its share of the CPU's time drawing a frame, 0 to 1
 */
record ProfileHeadline(int fps, String effect, double gpuShare, double cpuShare) {
}
