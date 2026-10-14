package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.world.phys.Vec3;

/**
 * What ray tracing a sound produced: the inputs to {@link AcousticFilters} that depend on the world's blocks.
 */
final class TraceResult {

    // Where the sound was traced from, moved out of a solid block or fluid if needed
    Vec3 soundPos = Vec3.ZERO;
    float occlusion;
    final float[] sendGains;
    final float[] bounceTotals;
    float sharedAirspace;
    int airspaceChecks = 1;

    TraceResult(int channels, int bounces) {
        this.sendGains = new float[channels];
        this.bounceTotals = new float[bounces];
    }

    void copyFrom(TraceResult other) {
        this.soundPos = other.soundPos;
        this.occlusion = other.occlusion;
        System.arraycopy(other.sendGains, 0, this.sendGains, 0, this.sendGains.length);
        System.arraycopy(other.bounceTotals, 0, this.bounceTotals, 0, this.bounceTotals.length);
        this.sharedAirspace = other.sharedAirspace;
        this.airspaceChecks = other.airspaceChecks;
    }
}
