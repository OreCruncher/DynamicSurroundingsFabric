// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * What particles cost the CPU, for the recording /dsprofile is making (see {@link ParticlesEvent}): for each of the
 * mod's particle classes, and all others together, how many were ticked and extracted (each frame's quads written) and
 * how long that took. Our particles are drawn among the game's, so this is the only place their CPU time shows; our
 * own layers' drawing is timed as well (ParticleLayerTimers). While nothing records, the game's particle code only
 * asks whether something does.
 * <p>
 * Particles may be ticked on several threads at once (Particle Core) or on a worker (Async Particles), so the counts
 * are kept in adders. Timing each particle costs two clock reads, so a recording of thousands of particles shows them
 * a little dearer than they are. Uses nothing from {@code jdk.jfr}, so the game's code can call it in a Java without
 * that module.
 */
public final class ParticleSamples {

    /**
     * The name all particles not the mod's are counted under
     */
    static final String OTHERS = "Others";
    private static final String OURS = "org.orecruncher.dsurround.";

    /**
     * One kind of particle's counts
     */
    static final class Counters {
        final String type;
        final LongAdder ticked = new LongAdder();
        final LongAdder tickNanos = new LongAdder();
        final LongAdder extracted = new LongAdder();
        final LongAdder extractNanos = new LongAdder();

        Counters(String type) {
            this.type = type;
        }
    }

    /**
     * What one kind of particle cost since the last take
     */
    record Taken(String type, long ticked, long tickNanos, long extracted, long extractNanos) {
    }

    /**
     * What all of them cost since the last take, with the client ticks and frames it covers
     */
    record Second(long ticks, long frames, List<Taken> types) {
    }

    private static final Map<String, Counters> BY_TYPE = new ConcurrentHashMap<>();
    private static final Counters OTHER = counters(OTHERS);
    private static final ClassValue<Counters> OF_CLASS = new ClassValue<>() {
        @Override
        protected Counters computeValue(Class<?> type) {
            return type.getName().startsWith(OURS) ? counters(nameOf(type)) : OTHER;
        }
    };
    private static final LongAdder TICKS = new LongAdder();
    private static final LongAdder FRAMES = new LongAdder();
    private static volatile boolean recording;

    private ParticleSamples() {
    }

    private static Counters counters(String type) {
        return BY_TYPE.computeIfAbsent(type, Counters::new);
    }

    /**
     * A class's name without its package; a nested class keeps the class it is in
     */
    static String nameOf(Class<?> type) {
        var name = type.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * Whether a recording wants the particles' times; asked for each particle
     */
    public static boolean isRecording() {
        return recording;
    }

    /**
     * A particle was ticked, taking this long; any thread
     */
    public static void ticked(Object particle, long nanos) {
        var counters = OF_CLASS.get(particle.getClass());
        counters.ticked.increment();
        counters.tickNanos.add(nanos);
    }

    /**
     * A particle's quad was written for the frame, taking this long; the render thread
     */
    public static void extracted(Object particle, long nanos) {
        var counters = OF_CLASS.get(particle.getClass());
        counters.extracted.increment();
        counters.extractNanos.add(nanos);
    }

    /**
     * The particles were ticked once more
     */
    public static void tick() {
        TICKS.increment();
    }

    /**
     * The particles were extracted for another frame
     */
    public static void frame() {
        FRAMES.increment();
    }

    /**
     * What was counted since the last take, and starts again; kinds with nothing counted left out
     */
    static Second take() {
        var types = new ArrayList<Taken>();
        for (var counters : BY_TYPE.values()) {
            long ticked = counters.ticked.sumThenReset();
            long tickNanos = counters.tickNanos.sumThenReset();
            long extracted = counters.extracted.sumThenReset();
            long extractNanos = counters.extractNanos.sumThenReset();
            if (ticked != 0 || extracted != 0)
                types.add(new Taken(counters.type, ticked, tickNanos, extracted, extractNanos));
        }
        return new Second(TICKS.sumThenReset(), FRAMES.sumThenReset(), types);
    }

    static void start() {
        take();
        recording = true;
    }

    static void stop() {
        recording = false;
    }
}
