// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.RenderTimers;
import org.orecruncher.dsurround.config.AuroraQuality;
import org.orecruncher.dsurround.effects.aurora.Aurora;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A Java Flight Recorder recording of the running game, made in the game itself: the JDK's "profile" settings, only for
 * the events known to hold nothing that could tell who or where the player is ({@link #ALLOWED}), with what it was made on written into it once
 * ({@link EnvironmentEvent}, a {@link FeatureEvent} for each optional hook, conflict and setting, and a {@link ModEvent}
 * for each installed mod), the mod's render
 * timers once a second ({@link RenderTimerEvent}), the frames' lengths and the whole frame's drawing time once a second
 * ({@link FramesEvent}), each client tick handler's time every tick
 * ({@link HandlerTickEvent}), what random block effects did by distance once a second
 * ({@link RandomBlockSamplesEvent}), the classic aurora's shape and screen coverage once a second ({@link AuroraEvent}), the realistic
 * aurora's night, quality and pixels marched once a second ({@link RealisticAuroraEvent}), each kind of particle's CPU
 * time once a second ({@link ParticlesEvent}) and what enhanced sound
 * processing did once a second ({@link EnhancedSoundsEvent}), and what the mod's block and biome queries cost once a
 * second ({@link BlockQueriesEvent}). The only class besides those events to use {@code jdk.jfr}, so nothing loads it until a
 * recording is made.
 * <p>
 * Players may send recordings to the mod's authors, so what is private is kept out three times over: every other
 * event is turned off for the recording, the file is written again with only those allowed, in case anything recorded
 * others anyway (another recording running at the same time turns on what it asks for, in ours too), and the paths of
 * the player's folders are redacted (see {@link Redaction}). It is written under a name of its own until it
 * is redacted, so a failure leaves nothing unredacted under the recording's name ({@link ProfileFiles} clears it away).
 */
final class ProfileRecording {

    /**
     * The events a recording keeps, besides the mod's own ({@code dsurround.}): numbers, and the names of classes,
     * methods and threads. The game's: the frame rate, the built-in server's tick time, and how long a world took to
     * load. The JDK's: the machine (processor, memory), samples of what each thread was running and allocating, garbage
     * collection, threads waiting (locks, parking, sleeping), the JIT compiler, safepoints and VM operations, and the
     * JVM's number and switch flags (the heap's size, the collector). Any other is left out, a new JDK's or another
     * mod's events among them, until it has been looked at for what it carries: one that can carry anything private
     * goes in {@link #PRIVATE} instead.
     */
    static final Set<String> ALLOWED = Set.of(
            "minecraft.ClientFps",
            "minecraft.ServerTickTime",
            "minecraft.LoadWorld",
            // The machine
            "jdk.CPUInformation",
            "jdk.CPULoad",
            "jdk.CPUTimeStampCounter",
            "jdk.ThreadCPULoad",
            "jdk.ThreadContextSwitchRate",
            "jdk.PhysicalMemory",
            "jdk.SwapSpace",
            "jdk.ResidentSetSize",
            "jdk.NativeMemoryUsage",
            "jdk.NativeMemoryUsageTotal",
            "jdk.VirtualizationInformation",
            // What threads run and allocate
            "jdk.ExecutionSample",
            "jdk.NativeMethodSample",
            "jdk.ObjectAllocationSample",
            "jdk.ThreadAllocationStatistics",
            "jdk.JavaThreadStatistics",
            "jdk.ThreadStart",
            "jdk.ThreadEnd",
            "jdk.ClassLoadingStatistics",
            "jdk.ExceptionStatistics",
            "jdk.DirectBufferStatistics",
            // Garbage collection
            "jdk.GarbageCollection",
            "jdk.YoungGarbageCollection",
            "jdk.OldGarbageCollection",
            "jdk.G1GarbageCollection",
            "jdk.ParallelOldGarbageCollection",
            "jdk.ZYoungGarbageCollection",
            "jdk.ZOldGarbageCollection",
            "jdk.ZAllocationStall",
            "jdk.GCPhasePause",
            "jdk.GCPhasePauseLevel1",
            "jdk.GCPhasePauseLevel2",
            "jdk.GCPhaseConcurrent",
            "jdk.GCPhaseConcurrentLevel1",
            "jdk.GCPhaseConcurrentLevel2",
            "jdk.GCPhaseParallel",
            "jdk.GCCPUTime",
            "jdk.GCHeapSummary",
            "jdk.G1HeapSummary",
            "jdk.PSHeapSummary",
            "jdk.MetaspaceSummary",
            "jdk.GCHeapMemoryUsage",
            "jdk.GCHeapMemoryPoolUsage",
            "jdk.GCReferenceStatistics",
            "jdk.GCConfiguration",
            "jdk.GCHeapConfiguration",
            "jdk.YoungGenerationConfiguration",
            "jdk.SystemGC",
            "jdk.EvacuationFailed",
            "jdk.PromotionFailed",
            "jdk.ConcurrentModeFailure",
            "jdk.MetaspaceOOM",
            // Threads waiting
            "jdk.JavaMonitorEnter",
            "jdk.JavaMonitorWait",
            "jdk.ThreadPark",
            "jdk.ThreadSleep",
            "jdk.VirtualThreadPinned",
            // The JIT compiler, safepoints and VM operations
            "jdk.Compilation",
            "jdk.CompilationFailure",
            "jdk.CompilerConfiguration",
            "jdk.CompilerStatistics",
            "jdk.CodeCacheConfiguration",
            "jdk.CodeCacheStatistics",
            "jdk.CodeCacheFull",
            "jdk.Deoptimization",
            "jdk.SafepointBegin",
            "jdk.ExecuteVMOperation",
            // The JVM's flags but its text ones (paths), and what the recorder lost
            "jdk.BooleanFlag",
            "jdk.IntFlag",
            "jdk.UnsignedIntFlag",
            "jdk.LongFlag",
            "jdk.UnsignedLongFlag",
            "jdk.DoubleFlag",
            "jdk.DataLoss");

    /**
     * Whether a recording keeps events of the type: the mod's own and {@link #ALLOWED}'s, never {@link #PRIVATE}'s
     */
    static boolean isAllowed(String type) {
        return (type.startsWith("dsurround.") || ALLOWED.contains(type)) && !PRIVATE.contains(type);
    }

    /**
     * Events known to carry something private, kept out even if they were allowed by mistake: the command line (the game's arguments hold the player's name, id and
     * access token), environment variables and system properties (names, folders), the machine's other processes, file
     * paths (which name the player's account), network addresses (the server's, and the machine's own), certificates,
     * exception messages (which often hold paths), loaded libraries and agents (paths), the OS (its description holds
     * the machine's name on some systems), thread dumps, the recording's own destination, and where code was loaded
     * from (finalizer statistics, and modules, which on NeoForge are the mods' jars). The game's own: its network
     * events, which hold the server's address, and its chunk and region events, which hold coordinates and the save's
     * folder name (off unless another recording, vanilla's /jfr, turns them on). The JVM's text flags and heap dumps, which hold paths (a heap dump's
     * destination, the -XX: options launchers set to an instance's folders). What is shared between events, out of reach of leaving events out, is
     * redacted afterwards (see {@link Redaction}).
     */
    static final Set<String> PRIVATE = Set.of(
            "jdk.JVMInformation",
            "jdk.InitialEnvironmentVariable",
            "jdk.InitialSystemProperty",
            "jdk.InitialSecurityProperty",
            "jdk.SecurityPropertyModification",
            "jdk.SecurityProviderService",
            "jdk.OSInformation",
            "jdk.SystemProcess",
            "jdk.ProcessStart",
            "jdk.NativeLibrary",
            "jdk.NativeLibraryLoad",
            "jdk.NativeLibraryUnload",
            "jdk.JavaAgent",
            "jdk.NativeAgent",
            "jdk.NetworkUtilization",
            "jdk.FileRead",
            "jdk.FileWrite",
            "jdk.FileForce",
            "jdk.SocketRead",
            "jdk.SocketWrite",
            "jdk.TLSHandshake",
            "jdk.X509Certificate",
            "jdk.X509Validation",
            "jdk.JavaExceptionThrow",
            "jdk.JavaErrorThrow",
            "jdk.Deserialization",
            "jdk.ThreadDump",
            "jdk.ActiveRecording",
            "jdk.FinalizerStatistics",
            "jdk.ModuleExport",
            "jdk.ModuleRequire",
            "jdk.StringFlag",
            "jdk.StringFlagChanged",
            "jdk.HeapDump",
            "minecraft.NetworkSummary",
            "minecraft.PacketReceived",
            "minecraft.PacketSent",
            "minecraft.ChunkGeneration",
            "minecraft.StructureGeneration",
            "minecraft.ChunkRegionRead",
            "minecraft.ChunkRegionWrite");

    // Set by the first warm-up, on the profile's thread; read on the render thread
    private static volatile boolean warm;

    private ProfileRecording() {
    }

    /**
     * Whether Java Flight Recorder can record here: this Java has it, and it hasn't been turned off
     */
    static boolean isAvailable() {
        return FlightRecorder.isAvailable();
    }

    /**
     * Records for {@code length}, then writes the recording to {@code file}, without what is private. Blocks the thread
     * it is called on for that long, so not the render thread: {@code environment} is captured there beforehand.
     */
    static void record(Path file, Duration length, ProfileEnvironment environment) throws IOException, ParseException, InterruptedException {
        var settings = settings();
        var sources = sources();
        register(sources);

        var unfinished = file.resolveSibling(file.getFileName() + ProfileFiles.UNFINISHED);
        // A recording's name until it is redacted, which ProfileFiles clears away if left (see its UNFINISHED)
        var unredacted = file.resolveSibling(file.getFileName().toString().replace(".jfr", "-unredacted.jfr") + ProfileFiles.UNFINISHED);
        for (var source : sources)
            if (source.write() != null)
                FlightRecorder.addPeriodicEvent(source.type(), source.write());
        try {
            try (var recording = new Recording(settings)) {
                recording.setName("Dynamic Surroundings");
                recording.setToDisk(true);
                enable(recording, sources);
                recording.start();
                for (var source : sources)
                    source.start().run();
                EnvironmentEvent.write(environment);
                FeatureEvent.write(environment.features());
                ModEvent.write(environment.modList());
                Thread.sleep(length.toMillis());
                stop(sources);
                // What has been counted since the last second's write
                for (var source : sources)
                    if (source.flush())
                        Objects.requireNonNull(source.write()).run();
                recording.stop();
                recording.dump(unfinished);
            }
            // Written again, with only the allowed events: no other should be there, but another recording running at
            // the same time turns on what it asks for, in this one too
            try (var recorded = new RecordingFile(unfinished)) {
                recorded.write(unredacted, event -> isAllowed(event.getEventType().getName()));
            }
            // And the paths of the player's folders redacted: shared between events, out of their reach. Only then
            // under the recording's name
            Redaction.redact(unredacted);
            Files.move(unredacted, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            stop(sources);
            for (var source : sources)
                if (source.write() != null)
                    FlightRecorder.removePeriodicEvent(source.write());
            Files.deleteIfExists(unfinished);
            Files.deleteIfExists(unredacted);
        }
    }

    /**
     * Whether a recording has run since the game started, so the next starts without the first's slowdown
     */
    static boolean isWarm() {
        return warm;
    }

    /**
     * Runs a recording with the same settings for {@code length}, kept nowhere, the first time only, so the recording
     * after it starts as one would later in the session. The first recording since the game started slowed the game for
     * seconds after it began (frames of up to a second in a large modpack; later ones showed nothing of the kind): JFR
     * setting itself up and instrumenting the event classes. Made while the scene settles, it isn't recorded. Later,
     * just waits. Not the render thread.
     */
    static void warmUp(Duration length) throws IOException, ParseException, InterruptedException {
        if (warm) {
            Thread.sleep(length.toMillis());
            return;
        }
        var sources = sources();
        register(sources);
        try (var recording = new Recording(settings())) {
            recording.setName("Dynamic Surroundings warm-up");
            recording.setToDisk(false);
            enable(recording, sources);
            recording.start();
            Thread.sleep(length.toMillis());
            recording.stop();
        }
        warm = true;
    }

    // The JDK's "profile" settings for the allowed events, and every other event type known turned off: one the
    // settings don't name records as its own annotations say, and most say it's on (the game's network summary, which
    // holds the server's address; another mod's). One registered later is left out when the file is written again.
    // Ours are set by enable
    private static HashMap<String, String> settings() throws IOException, ParseException {
        var settings = new HashMap<String, String>();
        Configuration.getConfiguration("profile").getSettings().forEach((key, value) -> {
            if (isAllowed(key.substring(0, Math.max(key.indexOf('#'), 0))))
                settings.put(key, value);
        });
        for (var type : FlightRecorder.getFlightRecorder().getEventTypes())
            if (!isAllowed(type.getName()))
                settings.put(type.getName() + "#enabled", "false");
        return settings;
    }

    /**
     * One of the mod's event types and what feeds it: what writes it once a second (null for one written as things
     * happen, or once), whether that runs once more when the recording ends, for what was counted since its last
     * second, and the sampler started and stopped with the recording. Each event type is one row in {@link #sources},
     * which registers, enables, hooks, starts, flushes and stops it, so none can be left counting after a recording.
     */
    record Source(Class<? extends Event> type, @Nullable Runnable write, boolean flush, Runnable start, Runnable stop) {

        private static final Runnable NOTHING = () -> {
        };

        // Written once, as the recording starts
        static Source once(Class<? extends Event> type) {
            return new Source(type, null, false, NOTHING, NOTHING);
        }

        // Written by its sampler as things happen
        static Source live(Class<? extends Event> type, Runnable start, Runnable stop) {
            return new Source(type, null, false, start, stop);
        }

        // Written once a second, and what was counted since the last second when the recording ends
        static Source sampled(Class<? extends Event> type, Runnable write, Runnable start, Runnable stop) {
            return new Source(type, write, true, start, stop);
        }
    }

    /**
     * The mod's event types and what feeds them, made afresh for each recording (the totals they add up in)
     */
    static List<Source> sources() {
        var samples = new SampleTotals();
        var auroras = new AuroraTotals();
        var realistic = new RealisticAuroraTotals();
        return List.of(
                Source.once(EnvironmentEvent.class),
                Source.once(FeatureEvent.class),
                Source.once(ModEvent.class),
                Source.live(HandlerTickEvent.class, () -> HandlerTicks.start(ProfileRecording::writeHandlerTick), HandlerTicks::stop),
                // The render timers' running averages, read: nothing is counted to flush
                new Source(RenderTimerEvent.class, ProfileRecording::writeTimers, false, Source.NOTHING, Source.NOTHING),
                // Not flushed: an event holds a second's frames, and the summaries count them as a second's
                new Source(FramesEvent.class, ProfileRecording::writeFrames, false, FrameTimes::start, FrameTimes::stop),
                Source.sampled(RandomBlockSamplesEvent.class, samples::write, () -> RandomBlockSamples.start(samples::add), RandomBlockSamples::stop),
                Source.sampled(AuroraEvent.class, auroras::write, () -> AuroraSamples.start(auroras::add), AuroraSamples::stop),
                Source.sampled(RealisticAuroraEvent.class, realistic::write, () -> RealisticAuroraSamples.start(realistic::add), RealisticAuroraSamples::stop),
                Source.sampled(ParticlesEvent.class, ProfileRecording::writeParticles, ParticleSamples::start, ParticleSamples::stop),
                Source.sampled(EnhancedSoundsEvent.class, ProfileRecording::writeSounds, SoundSamples::start, SoundSamples::stop),
                Source.sampled(BlockQueriesEvent.class, ProfileRecording::writeBlockQueries, BlockQuerySamples::start, BlockQuerySamples::stop));
    }

    // Registered before the recording starts, so starting it instruments them with the JDK's, in one pause. Left to
    // their first event, each paused every thread for its own, there and then: in a game, 100 ms, inside a tick being
    // timed. The periodic ones are registered with their hooks, by the recording; registering them again is harmless.
    private static void register(List<Source> sources) {
        for (var source : sources)
            FlightRecorder.register(source.type());
    }

    private static void enable(Recording recording, List<Source> sources) {
        for (var source : sources) {
            var settings = recording.enable(source.type());
            if (source.write() != null)
                settings.withPeriod(Duration.ofSeconds(1));
        }
    }

    // Each stop is safe to repeat: the recording's end, then again in its finally, for a recording that failed
    private static void stop(List<Source> sources) {
        for (var source : sources)
            source.stop().run();
    }

    /**
     * An event for each render timer that has measured in the last second. The timers are written on the render
     * thread and read here; a value a frame out of date makes no difference
     */
    private static void writeTimers() {
        for (var timer : RenderTimers.ALL) {
            if (timer.isStale())
                continue;
            var event = new RenderTimerEvent();
            event.effect = timer.name();
            event.gpuAverage = timer.getMSecs();
            event.gpuPeak = timer.getPeakMSecs();
            event.cpuAverage = timer.getCpuMSecs();
            event.cpuPeak = timer.getCpuPeakMSecs();
            event.count = timer.getCount();
            event.perTick = timer.isPerTick();
            event.commit();
        }
    }

    /**
     * An event for the frames since the last, if there were any
     */
    private static void writeFrames() {
        var second = FrameTimes.take();
        if (second != null)
            FramesEvent.write(second);
    }

    /**
     * An event for each kind of particle counted since the last, with the ticks and frames it covers
     */
    private static void writeParticles() {
        var second = ParticleSamples.take();
        for (var type : second.types()) {
            var event = new ParticlesEvent();
            event.type = type.type();
            event.ticks = second.ticks();
            event.frames = second.frames();
            event.ticked = type.ticked();
            event.tickTime = type.tickNanos();
            event.extracted = type.extracted();
            event.extractTime = type.extractNanos();
            event.commit();
        }
    }

    /**
     * An event for what enhanced sound processing did since the last
     */
    private static void writeSounds() {
        EnhancedSoundsEvent.write(SoundSamples.isAvailable(), SoundSamples.take());
    }

    private static void writeBlockQueries() {
        BlockQueriesEvent.write(BlockQuerySamples.take());
    }

    /**
     * An event for one handler's time in one tick, from the client thread
     */
    private static void writeHandlerTick(long tick, String handler, long nanos, boolean ran) {
        var event = new HandlerTickEvent();
        event.tick = tick;
        event.handler = handler;
        event.time = nanos;
        event.ran = ran;
        event.commit();
    }

    /**
     * Random block samples added up between the client thread's ticks and the recorder's writes, once a second
     */
    private static final class SampleTotals {
        private final long[] totals = new long[RandomBlockSamples.SHELLS * RandomBlockSamples.KINDS];
        private int ticks;
        private int range;

        synchronized void add(int range, int[] counts) {
            for (int i = 0; i < this.totals.length; i++)
                this.totals[i] += counts[i];
            this.ticks++;
            this.range = range;
        }

        /**
         * An event for each shell with anything in it since the last write
         */
        synchronized void write() {
            if (this.ticks == 0)
                return;
            for (int shell = 0; shell < RandomBlockSamples.SHELLS; shell++) {
                int base = shell * RandomBlockSamples.KINDS;
                boolean any = false;
                for (int kind = 0; kind < RandomBlockSamples.KINDS; kind++)
                    any |= this.totals[base + kind] != 0;
                if (!any)
                    continue;
                var event = new RandomBlockSamplesEvent();
                event.shell = shell;
                event.ticks = this.ticks;
                event.range = this.range;
                event.samples = this.totals[base + RandomBlockSamples.SAMPLES];
                event.tailSamples = this.totals[base + RandomBlockSamples.TAIL];
                event.effectBlocks = this.totals[base + RandomBlockSamples.EFFECT_BLOCKS];
                event.starts = this.totals[base + RandomBlockSamples.STARTS];
                event.sounds = this.totals[base + RandomBlockSamples.SOUNDS];
                event.particles = this.totals[base + RandomBlockSamples.PARTICLES];
                event.commit();
            }
            Arrays.fill(this.totals, 0);
            this.ticks = 0;
        }
    }

    /**
     * The classic aurora's frames added up between the render thread's draws and the recorder's writes, once a second
     */
    private static final class AuroraTotals {
        private @Nullable Aurora aurora;
        private int frames;
        private double strength;
        private double pixels;
        private double share;

        synchronized void add(Aurora aurora, float strength, double pixels, int screenPixels) {
            this.aurora = aurora;
            this.frames++;
            this.strength += strength;
            this.pixels += pixels;
            this.share += screenPixels > 0 ? pixels / screenPixels : 0;
        }

        /**
         * An event for the aurora drawn since the last write, if it was
         */
        synchronized void write() {
            var drawn = this.aurora;
            if (drawn == null || this.frames == 0)
                return;
            var event = new AuroraEvent();
            event.frames = this.frames;
            event.bands = drawn.bands();
            event.distance = drawn.distance();
            event.length = drawn.length();
            event.altitude = drawn.altitude();
            event.height = drawn.height();
            event.heading = (float) Math.toDegrees(drawn.heading());
            event.strength = (float) (this.strength / this.frames);
            event.pixels = this.pixels / this.frames;
            event.screenShare = this.share / this.frames;
            event.commit();
            this.aurora = null;
            this.frames = 0;
            this.strength = 0;
            this.pixels = 0;
            this.share = 0;
        }
    }

    /**
     * The realistic aurora's frames added up between the render thread's draws and the recorder's writes, once a second
     */
    private static final class RealisticAuroraTotals {
        private @Nullable RealisticAurora aurora;
        private @Nullable RealisticAurora.Phase phase;
        private @Nullable AuroraQuality quality;
        private int frames;
        private int width;
        private int height;
        private double strength;
        private double rayField;
        private double share;
        private double pixels;

        synchronized void add(RealisticAurora aurora, RealisticAurora.Moment moment, AuroraQuality quality, float strength,
                              int width, int height, double marchedShare) {
            this.aurora = aurora;
            this.phase = moment.phase();
            this.quality = quality;
            this.width = width;
            this.height = height;
            this.frames++;
            this.strength += strength;
            this.rayField += moment.rayField() * moment.gain();
            this.share += marchedShare;
            this.pixels += marchedShare * width * height;
        }

        /**
         * An event for the aurora drawn since the last write, if it was
         */
        synchronized void write() {
            var drawn = this.aurora;
            var at = this.phase;
            var how = this.quality;
            if (drawn == null || at == null || how == null || this.frames == 0)
                return;
            var event = new RealisticAuroraEvent();
            event.frames = this.frames;
            event.activity = drawn.activity().name();
            event.color = drawn.color().name();
            event.phase = at.name();
            event.arcs = Math.min(drawn.arcs().size(), RealisticAurora.MAX_ARCS);
            event.rayField = (float) (this.rayField / this.frames);
            event.quality = how.name();
            event.fieldSamples = how.fieldSamples();
            event.width = this.width;
            event.height = this.height;
            event.strength = (float) (this.strength / this.frames);
            event.marchedShare = this.share / this.frames;
            event.pixels = this.pixels / this.frames;
            event.commit();
            this.aurora = null;
            this.phase = null;
            this.quality = null;
            this.frames = 0;
            this.strength = 0;
            this.rayField = 0;
            this.share = 0;
            this.pixels = 0;
        }
    }
}
