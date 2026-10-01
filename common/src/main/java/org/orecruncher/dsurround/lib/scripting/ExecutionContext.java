package org.orecruncher.dsurround.lib.scripting;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Expression;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    // Scripts that have already logged a runtime error, so a broken script run every tick logs only once
    private final Set<String> reportedRuntimeErrors = ConcurrentHashMap.newKeySet();

    // Incremented whenever definitions change so that compile failures are retried with the new definitions
    private volatile int generation;

    public ExecutionContext(final String contextName, IModLog logger) {
        this.logger = logger;
        this.contextName = contextName;
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
        if (this.reportedRuntimeErrors.add(script.asString())) {
            // Only reached once per script, so creating the exception for the log entry costs nothing per tick
            try {
                ScriptHelpers.toBoolean(value);
            } catch (final ScriptException e) {
                this.logger.error(e, "Script result cannot be converted to a boolean: %s", script.asString());
            }
        }
        return false;
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
        if (this.reportedRuntimeErrors.add(script.asString()))
            this.logger.error(e, "%s", e.getMessageForLogging(script.asString()));
    }

    private void reportUnexpectedError(final Script script, final Throwable t) {
        if (this.reportedRuntimeErrors.add(script.asString()))
            this.logger.error(t, "Error executing script: %s", script.asString());
    }

    private void onDefinitionsChanged() {
        this.generation++;
        this.failedCompiles.clear();
        this.reportedRuntimeErrors.clear();
    }

    private Expression getExpression(final Script script) {
        var gen = this.generation;
        var cached = script.getCompiledExpression(this, gen);
        if (cached != null)
            return cached;
        var compiled = this.generateExpression(script.asString());
        script.setCompiledScript(this, gen, compiled);
        return compiled;
    }

    private Expression generateExpression(final String script) {
        var cached = this.expressions.get(script);
        if (cached != null)
            return cached;
        cached = this.failedCompiles.get(script);
        if (cached != null)
            return cached;

        try {
            var compiled = this.engine.compile(script);
            this.expressions.put(script, compiled);
            return compiled;
        } catch (final ScriptException e) {
            var msg = e.getMessageForLogging(script);
            this.logger.error(e, "%s", msg);
            var error = e.asExpression();
            this.failedCompiles.put(script, error);
            return error;
        } catch (final Throwable t) {
            this.logger.error(t, "Error compiling script: %s", t.getMessage());
            var error = makeErrorFunction(t);
            this.failedCompiles.put(script, error);
            return error;
        }
    }

    private static Expression makeErrorFunction(Throwable t) {
        var msg = String.valueOf(t.getMessage());
        return () -> msg;
    }
}
