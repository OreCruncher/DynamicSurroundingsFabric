package org.orecruncher.dsurround.profiling;

import dev.architectury.platform.Platform;
import org.orecruncher.dsurround.lib.Library;

/**
 * Starts the development bench (the {@code org.orecruncher.dsurround.bench} package: scenario runs and /dsbench) in a
 * development run. That package is left out of the published jars (the platform builds check), so it is only named
 * here, never referenced: in a published jar, or outside development, this does nothing.
 */
public final class BenchHook {

    /**
     * The scenario file to run, set by the bench run configurations (runClientBench)
     */
    public static final String SCENARIO_PROPERTY = "dsurround.scenario";
    private static final String BENCH = "org.orecruncher.dsurround.bench.Bench";

    private BenchHook() {
    }

    public static void start() {
        if (!Platform.isDevelopmentEnvironment())
            return;
        try {
            var bench = Class.forName(BENCH);
            bench.getMethod("start", String.class).invoke(null, System.getProperty(SCENARIO_PROPERTY, ""));
        } catch (ClassNotFoundException e) {
            // Not in this build
        } catch (ReflectiveOperationException e) {
            Library.LOGGER.error(e, "Unable to start the development bench");
        }
    }
}
