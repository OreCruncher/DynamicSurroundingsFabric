package org.orecruncher.dsurround.config.libraries.impl;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Applies configuration rules so that one rule failing doesn't stop the others. The libraries use it wherever rules
 * from config files are matched and applied, which may be packs' own rules.
 */
final class RuleGuard {

    private RuleGuard() {
    }

    /**
     * Runs {@code action} for each rule. A rule that throws is passed to {@code onFailure} (typically logged once per
     * reload) and the rest still run. Errors the JVM can't recover from (out of memory, stack overflow) are rethrown.
     */
    static <R> void forEach(Iterable<R> rules, Consumer<R> action, BiConsumer<R, Throwable> onFailure) {
        for (var rule : rules) {
            try {
                action.accept(rule);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable t) {
                onFailure.accept(rule, t);
            }
        }
    }
}
