package org.orecruncher.dsurround.lib.threading;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records errors and warnings logged by the code under test, from any thread.
 */
public final class RecordingLog implements IModLog {

    public record Entry(Level level, String message, @Nullable Throwable throwable) {
    }

    public final List<Entry> entries = new CopyOnWriteArrayList<>();

    public List<Entry> at(Level level) {
        return this.entries.stream().filter(e -> e.level() == level).toList();
    }

    @Override
    public boolean isDebugging() {
        return false;
    }

    @Override
    public boolean isTracing(int mask) {
        return false;
    }

    @Override
    public void log(Level level, @Nullable Throwable t, String format, @Nullable Object... params) {
        var message = params == null || params.length == 0 ? format : String.format(format, params);
        this.entries.add(new Entry(level, message, t));
    }
}
