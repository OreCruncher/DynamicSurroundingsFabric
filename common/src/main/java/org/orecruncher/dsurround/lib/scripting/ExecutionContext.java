// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Holds a script engine along with its variable sets, and evaluates scripts against it.
 * <p>
 * Configuration ({@link #add(VariableSet)} and {@link #configureScripting(IConfigureScripting)}) is expected to
 * happen from a single thread during setup. Evaluation is safe from multiple threads as far as the engine is
 * concerned; whether it is safe overall depends on the variable sets being thread-safe.
 */
public final class ExecutionContext {

    private final IModLog logger;
    private final String contextName;
    private final ScriptEngine engine;
    private final List<VariableSet> variables = new ArrayList<>(8);
    private final Set<String> variableSetNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    // Successfully compiled scripts, keyed by script text
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>(32);
    // Scripts that failed to compile with the current definitions, keyed by script text
    private final Map<String, Expression> failedCompiles = new ConcurrentHashMap<>();
    // Runtime errors, keyed by script text, so a broken script run every tick logs only once (per definition change)
    private final LogThrottle<String> scriptFailures;

    // Incremented whenever definitions change so that compile failures are retried with the new definitions
    private volatile int generation;

    public ExecutionContext(final String contextName, IModLog logger) {
        this.logger = logger;
        this.contextName = contextName;
        this.scriptFailures = LogThrottle.oncePerKey(logger, "script errors", "the script definitions change");
        this.engine = new ScriptEngine();
        this.logger.info("[%s] Configured", this.contextName);
    }

    public void add(final VariableSet varSet) {
        Preconditions.checkNotNull(varSet);
        if (this.variableSetNames.contains(varSet.getSetName()))
            throw new IllegalStateException(String.format("Variable set '%s' already defined!", varSet.getSetName()));

        this.configureScripting(varSet);
        this.variableSetNames.add(varSet.getSetName());
        this.variables.add(varSet);
    }

    public void configureScripting(IConfigureScripting config) {
        config.configure(this.engine);
        this.onDefinitionsChanged();
    }

    /**
     * Defines the config files' constants and one section's functions, in place of what was defined before; scripts
     * compile again with them. Each problem is described to {@code problems}, and what it concerns is left out.
     */
    public void define(ScriptDefinitions definitions, ScriptDefinitions.Section functions, Consumer<String> problems) {
        Preconditions.checkNotNull(definitions);
        definitions.defineIn(this.engine, functions, problems);
        // Compiled scripts hold the definitions they were compiled with. The generation moves first, so a compile
        // begun with the old definitions doesn't put its result in the caches after they are cleared
        this.onDefinitionsChanged();
        this.expressions.clear();
    }

    public String getName() {
        return this.contextName;
    }

    public void tick() {
        this.variables.forEach(VariableSet::tick);
    }

    /**
     * Evaluates the script as a condition. A script that fails, returns null, or returns a value that cannot be
     * converted to a boolean is treated as false (and the problem is logged once).
     * <p>
     * This is the common call, so it evaluates directly to a boolean rather than going through
     * {@link #eval(Script)} and its Optional.
     */
    public boolean check(final Script script) {
        Preconditions.checkNotNull(script);
        Object value;
        try {
            value = this.getExpression(script).eval();
        } catch (final ScriptException e) {
            this.reportScriptError(script, e);
            return false;
        } catch (final Throwable t) {
            this.reportUnexpectedError(script, t);
            return false;
        }

        if (value instanceof Boolean b)
            return b;
        var converted = ScriptHelpers.tryToBoolean(value);
        if (converted != null)
            return converted;
        // The exception is only created if the message is logged, so a script failing every tick costs nothing extra
        this.scriptFailures.error(script.asString(), () -> conversionError(value),
                "Script result cannot be converted to a boolean: %s", script.asString());
        return false;
    }

    /**
     * The exception explaining why {@code value} isn't a boolean, for the log.
     */
    private static Throwable conversionError(final Object value) {
        try {
            ScriptHelpers.toBoolean(value);
        } catch (final ScriptException e) {
            return e;
        }
        return new IllegalArgumentException("Cannot convert to a boolean: " + value);
    }

    public Optional<Object> eval(final Script script) {
        Preconditions.checkNotNull(script);
        try {
            var func = this.getExpression(script);
            return Optional.ofNullable(func.eval());
        } catch (final ScriptException e) {
            this.reportScriptError(script, e);
            return Optional.of(e.getMessage());
        } catch (final Throwable t) {
            this.reportUnexpectedError(script, t);
            return Optional.of("ERROR? " + t.getMessage());
        }
    }

    private void reportScriptError(final Script script, final ScriptException e) {
        this.scriptFailures.error(script.asString(), e, () -> e.getMessageForLogging(script.asString()));
    }

    private void reportUnexpectedError(final Script script, final Throwable t) {
        this.scriptFailures.error(script.asString(), t, "Error executing script: %s", script.asString());
    }

    private void onDefinitionsChanged() {
        this.generation++;
        this.failedCompiles.clear();
        this.scriptFailures.reset();
    }

    private Expression getExpression(final Script script) {
        var gen = this.generation;
        var cached = script.getCompiledExpression(this, gen);
        if (cached != null)
            return cached;
        var compiled = this.generateExpression(script.asString(), gen);
        script.setCompiledScript(this, gen, compiled);
        return compiled;
    }

    // Kept only while the definitions it was compiled with are still the current ones
    private void cache(Map<String, Expression> cache, String script, Expression expression, int gen) {
        if (this.generation == gen)
            cache.put(script, expression);
    }

    private Expression generateExpression(final String script, int gen) {
        var cached = this.expressions.get(script);
        if (cached != null)
            return cached;
        cached = this.failedCompiles.get(script);
        if (cached != null)
            return cached;

        try {
            var compiled = this.engine.compile(script);
            this.cache(this.expressions, script, compiled, gen);
            return compiled;
        } catch (final ScriptException e) {
            var msg = e.getMessageForLogging(script);
            this.logger.error(e, "%s", msg);
            var error = e.asExpression();
            this.cache(this.failedCompiles, script, error, gen);
            return error;
        } catch (final Throwable t) {
            this.logger.error(t, "Error compiling script: %s", t.getMessage());
            var error = makeErrorFunction(t);
            this.cache(this.failedCompiles, script, error, gen);
            return error;
        }
    }

    private static Expression makeErrorFunction(Throwable t) {
        var msg = String.valueOf(t.getMessage());
        return () -> msg;
    }
}
