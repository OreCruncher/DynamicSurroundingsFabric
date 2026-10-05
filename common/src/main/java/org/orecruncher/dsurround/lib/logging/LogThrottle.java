package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Limits how often a repeated problem is logged, so something that fails on every tick, every block or every biome
 * doesn't flood the log. Two limits, either or both of which can apply:
 * <ul>
 *   <li>per key: at most {@code perKeyLimit} messages for any one key (a script, a config rule, ...). The last one
 *       allowed for a key notes that further occurrences won't be logged.</li>
 *   <li>overall: at most {@code totalLimit} messages in all. When that is reached, one notice says further
 *       messages won't be logged.</li>
 * </ul>
 * Messages over a limit are counted ({@link #suppressedCount()}) and dropped. {@link #reset()} starts afresh, for
 * example after a reload. Keys are compared with {@code equals}.
 * <p>
 * Thread-safe. The bookkeeping only runs when there is something to log, so it costs nothing on the normal path.
 *
 * @param <K> what messages are grouped by
 */
public final class LogThrottle<K> {

    private static final int UNLIMITED = Integer.MAX_VALUE;
    // Key used by the keyless methods, so every message counts toward the same (overall) limit
    private static final Object NO_KEY = new Object();

    // What acquire() decided, as bit flags
    private static final int DROP = 0;
    private static final int LOG = 1;
    private static final int LAST_FOR_KEY = 2;
    private static final int LAST_OVERALL = 4;

    private final IModLog logger;
    private final String description;
    private final @Nullable String resetCondition;
    private final int perKeyLimit;
    private final int totalLimit;

    private final Map<Object, Integer> perKeyCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalCount = new AtomicLong();
    private final AtomicLong suppressed = new AtomicLong();

    /**
     * @param logger         where messages go
     * @param description    what is being logged, in the plural, for the overall limit's notice ("block errors")
     * @param resetCondition when logging resumes, for the "not logged" notes ("the next reload"), or null if it
     *                       never does
     * @param perKeyLimit    most messages for one key, or Integer.MAX_VALUE for no limit
     * @param totalLimit     most messages overall, or Integer.MAX_VALUE for no limit
     */
    public LogThrottle(IModLog logger, String description, @Nullable String resetCondition, int perKeyLimit, int totalLimit) {
        if (perKeyLimit < 1 || totalLimit < 1)
            throw new IllegalArgumentException("Limits must be at least 1");
        this.logger = logger;
        this.description = description;
        this.resetCondition = resetCondition;
        this.perKeyLimit = perKeyLimit;
        this.totalLimit = totalLimit;
    }

    /**
     * Logs the first message for each key and drops the rest, until {@link #reset()}.
     */
    public static <K> LogThrottle<K> oncePerKey(IModLog logger, String description, @Nullable String resetCondition) {
        return new LogThrottle<>(logger, description, resetCondition, 1, UNLIMITED);
    }

    /**
     * Logs the first {@code limit} messages, then one notice, and drops the rest, until {@link #reset()}. Use the
     * keyless {@link #error(Throwable, String, Object...)}.
     */
    public static LogThrottle<Object> firstN(IModLog logger, String description, @Nullable String resetCondition, int limit) {
        return new LogThrottle<>(logger, description, resetCondition, UNLIMITED, limit);
    }

    public void error(K key, Throwable t, String format, Object... args) {
        this.errorFor(key, t, format, args);
    }

    /**
     * Like {@link #error(Object, Throwable, String, Object...)}, for when creating the exception is costly: the
     * supplier is only called if the message is actually logged.
     */
    public void error(K key, Supplier<? extends Throwable> cause, String format, Object... args) {
        int decision = this.acquire(key);
        if ((decision & LOG) != 0) {
            this.logger.error(cause.get(), format + this.suffix(decision), args);
            this.noticeIfLast(decision);
        }
    }

    /**
     * Like {@link #error(Object, Throwable, String, Object...)}, for when building the message is costly: the
     * supplier is only called if the message is actually logged. The message is used as is, not as a format.
     */
    public void error(K key, Throwable t, Supplier<String> message) {
        int decision = this.acquire(key);
        if ((decision & LOG) != 0) {
            this.logger.error(t, "%s", message.get() + this.suffix(decision));
            this.noticeIfLast(decision);
        }
    }

    public void warn(K key, String format, Object... args) {
        this.warnFor(key, format, args);
    }

    /**
     * For throttles without keys ({@link #firstN}): every message counts toward the overall limit.
     */
    public void error(Throwable t, String format, Object... args) {
        this.errorFor(NO_KEY, t, format, args);
    }

    /**
     * Messages dropped since the last reset.
     */
    public long suppressedCount() {
        return this.suppressed.get();
    }

    /**
     * Forgets all counts, so every key can be logged again.
     */
    public void reset() {
        this.perKeyCounts.clear();
        this.totalCount.set(0);
        this.suppressed.set(0);
    }

    // ---- Internals -------------------------------------------------------------------------------------------

    private void errorFor(Object key, Throwable t, String format, Object... args) {
        int decision = this.acquire(key);
        if ((decision & LOG) != 0) {
            this.logger.error(t, format + this.suffix(decision), args);
            this.noticeIfLast(decision);
        }
    }

    private void warnFor(Object key, String format, Object... args) {
        int decision = this.acquire(key);
        if ((decision & LOG) != 0) {
            this.logger.warn(format + this.suffix(decision), args);
            this.noticeIfLast(decision);
        }
    }

    /**
     * Counts a message for {@code key} and decides what happens to it.
     */
    private int acquire(Object key) {
        // Capped so a problem repeated billions of times can't wrap the count around and resume logging
        int forKey = this.perKeyCounts.merge(key, 1, (a, b) -> a == UNLIMITED ? a : a + 1);
        if (forKey > this.perKeyLimit) {
            this.suppressed.incrementAndGet();
            return DROP;
        }

        long overall = this.totalCount.incrementAndGet();
        if (overall > this.totalLimit) {
            this.suppressed.incrementAndGet();
            return DROP;
        }

        int decision = LOG;
        if (forKey == this.perKeyLimit)
            decision |= LAST_FOR_KEY;
        if (overall == this.totalLimit)
            decision |= LAST_OVERALL;
        return decision;
    }

    /**
     * The note added to the last message allowed for a key, or "" for the others.
     */
    private String suffix(int decision) {
        if ((decision & LAST_FOR_KEY) == 0)
            return "";
        return this.resetCondition != null
                ? " (further occurrences are not logged until " + this.resetCondition + ")"
                : " (further occurrences are not logged)";
    }

    /**
     * Logs the overall limit's notice, once, right after the last message it allows.
     */
    private void noticeIfLast(int decision) {
        if ((decision & LAST_OVERALL) == 0)
            return;
        if (this.resetCondition != null)
            this.logger.warn("%d %s logged; further ones are not logged until %s", this.totalLimit, this.description, this.resetCondition);
        else
            this.logger.warn("%d %s logged; further ones are not logged", this.totalLimit, this.description);
    }
}
