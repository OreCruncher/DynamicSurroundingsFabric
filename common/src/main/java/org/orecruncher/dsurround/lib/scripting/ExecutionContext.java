package org.orecruncher.dsurround.lib.scripting;

import org.apache.commons.lang3.StringEscapeUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.engine.Expression;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

import java.util.*;

public final class ExecutionContext implements IVariableAccess {

    private final IModLog logger;
    private final String contextName;
    private final ScriptEngine engine;
    private final Set<VariableSet> variables = new HashSet<>(8);
    private final Map<ScriptIdentifier, Expression> expressions = new HashMap<>(32);

    public ExecutionContext(final String contextName, IModLog logger) {
        this.logger = logger;
        this.contextName = contextName;
        this.engine = new ScriptEngine();
        this.logger.info("[%s] Configured", this.contextName);
    }

    public void add(final VariableSet varSet) {
        if (this.variables.contains(varSet))
            throw new IllegalStateException(String.format("Variable set '%s' already defined!", varSet.getSetName()));

        this.variables.add(varSet);
        this.configureScripting(varSet);
    }

    private void configureScripting(IConfigureScripting config) {
        config.configure(this.engine);
    }

    @Override
    public void put(String variableName, IScriptVariable value) {
        this.engine.defineVariable(variableName, value);
    }

    public String getName() {
        return this.contextName;
    }

    public void update() {
        this.variables.forEach(s -> s.update(this));
    }

    public boolean check(final Script script) {
        return ScriptHelpers.toBoolean(this.eval(script));
    }

    public Optional<Object> eval(final Script script) {
        try {
            var cached = script.getCompiledScript();
            var func = cached.orElseGet(() -> {
                var compiled = this.generateExpression(script.asString());
                script.setCompiledScript(compiled);
                return compiled;
            });

            return Optional.of(func.eval());

        } catch (final ScriptException e) {
            var msg = e.getMessageForLogging(script.asString());
            this.logger.error(e, msg);
            return Optional.of(e.getMessage());
        } catch (final Throwable t) {
            this.logger.error(t, "Error execution script: %s", script.asString());
            return Optional.of("ERROR? " + t.getMessage());
        }
    }

    private Expression generateExpression(final String script) {
        try {
            var scriptIdentifier = ScriptIdentifier.from(script);
            var cached = this.expressions.get(scriptIdentifier);
            if (cached == null) {
                cached = this.engine.compile(script);
                this.expressions.put(scriptIdentifier, cached);
            }
            return cached;
        } catch (ScriptException e) {
            var  msg = e.getMessageForLogging(script);
            this.logger.error(e, msg);
            return e.asExpression();
        } catch (final Throwable t) {
            this.logger.error(t, "Error compiling script: %s", t.getMessage());
            return makeErrorFunction(t);
        }
    }

    private Expression makeErrorFunction(Throwable t) {
        String s = String.format("\"%s\"", StringEscapeUtils.escapeJava(t.getMessage()));
        return generateExpression(s);
    }
}