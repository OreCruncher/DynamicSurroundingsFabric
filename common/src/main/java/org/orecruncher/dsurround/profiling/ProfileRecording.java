package org.orecruncher.dsurround.profiling;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.orecruncher.dsurround.effects.RenderTimers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;

/**
 * A Java Flight Recorder recording of the running game, made in the game itself: the JDK's "profile" settings, without
 * anything that could tell who or where the player is ({@link #PRIVATE}), with what it was made on written into it once
 * ({@link EnvironmentEvent}, and a {@link FeatureEvent} for each optional hook, conflict and setting), the mod's render
 * timers once a second ({@link RenderTimerEvent}) and each client tick handler's time every tick
 * ({@link HandlerTickEvent}). The only class besides those events to use {@code jdk.jfr}, so nothing loads it until a
 * recording is made.
 * <p>
 * Players may send recordings to the mod's authors, so what is private is kept out three times over: those events are
 * turned off for the recording, the file is written again without them, in case anything recorded them anyway, and the
 * player's home folder's path is redacted (see {@link Redaction}).
 */
final class ProfileRecording {

    /**
     * Events that can carry something private: the command line (the game's arguments hold the player's name, id and
     * access token), environment variables and system properties (names, folders), the machine's other processes, file
     * paths (which name the player's account), network addresses (the server's, and the machine's own), certificates,
     * exception messages (which often hold paths), loaded libraries and agents (paths), the OS (its description holds
     * the machine's name on some systems), thread dumps, the recording's own destination, and where code was loaded
     * from (finalizer statistics, and modules, which on NeoForge are the mods' jars). The game's own: its network
     * events, which hold the server's address. What is shared between events, out of reach of leaving events out, is
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
            "minecraft.NetworkSummary",
            "minecraft.PacketReceived",
            "minecraft.PacketSent");

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
        var settings = new HashMap<>(Configuration.getConfiguration("profile").getSettings());
        for (var type : PRIVATE)
            settings.put(type + "#enabled", "false");

        var unfinished = file.resolveSibling(file.getFileName() + ProfileFiles.UNFINISHED);
        Runnable timers = ProfileRecording::writeTimers;
        FlightRecorder.addPeriodicEvent(RenderTimerEvent.class, timers);
        try {
            try (var recording = new Recording(settings)) {
                recording.setName("Dynamic Surroundings");
                recording.setToDisk(true);
                recording.enable(RenderTimerEvent.class).withPeriod(Duration.ofSeconds(1));
                recording.enable(EnvironmentEvent.class);
                recording.enable(FeatureEvent.class);
                recording.enable(HandlerTickEvent.class);
                recording.start();
                HandlerTicks.start(ProfileRecording::writeHandlerTick);
                EnvironmentEvent.write(environment);
                FeatureEvent.write(environment.features());
                Thread.sleep(length.toMillis());
                HandlerTicks.stop();
                recording.stop();
                recording.dump(unfinished);
            }
            // Written again, without the private events: none should be there, but if anything recorded them anyway
            try (var recorded = new RecordingFile(unfinished)) {
                recorded.write(file, event -> !PRIVATE.contains(event.getEventType().getName()));
            }
            // And the home folder's path redacted: shared between events, out of their reach
            Redaction.redact(file, System.getProperty("user.home"));
        } finally {
            HandlerTicks.stop();
            FlightRecorder.removePeriodicEvent(timers);
            Files.deleteIfExists(unfinished);
        }
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
            event.commit();
        }
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
}
