package org.orecruncher.dsurround.lib.scripting;

import org.apache.commons.lang3.StringEscapeUtils;
import org.orecruncher.dsurround.lib.StringUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.engine.ExpressionTree;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptEngine;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;

import java.util.*;

public final class ExecutionContext implements IVariableAccess {

    private final IModLog logger;
    private final String contextName;
    private final ScriptEngine engine;
    private final Set<VariableSet> variables = new HashSet<>(8);
    private final Map<ScriptIdentifier, ExpressionTree> expressions = new HashMap<>(32);

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
        final Optional<Object> result = this.eval(script);
        if (result.isPresent())
            return "true".equalsIgnoreCase(result.toString());
        return false;
    }

    public Optional<Object> eval(final Script script) {
        var cached = script.getCompiledScript();
        var func = cached.orElseGet(() -> {
            var compiled = makeExpressionTree(script.asString());
            script.setCompiledScript(compiled);
            return compiled;
        });

        try {
            return func.eval();
        } catch (final Throwable t) {
            this.logger.error(t, "Error execution script: %s", script.asString());
            return Optional.of("ERROR? " + t.getMessage());
        }
    }

    private ExpressionTree makeExpressionTree(final String script) {
        try {
            var scriptIdentifier = ScriptIdentifier.from(script);
            var cached = this.expressions.get(scriptIdentifier);
            if (cached == null) {
                cached = this.engine.compile(script);
                this.expressions.put(scriptIdentifier, cached);
            }
            return cached;
        } catch (ScriptException e) {
            var locus = StringUtils.truncateWithCarat(script, e.getPosition(), 50);
            var msg = "Error parsing script: %s\n%s\n%s".formatted(e.getMessage(), locus.text(), locus.caratLine());
            this.logger.error(e, msg);
            return makeErrorFunction(e);
        } catch (final Throwable t) {
            this.logger.error(t, "Error compiling script: %s", t.getMessage());
            return makeErrorFunction(t);
        }
    }

    private ExpressionTree makeErrorFunction(Throwable t) {
        String s = String.format("\"%s\"", StringEscapeUtils.escapeJava(t.getMessage()));
        return makeExpressionTree(s);
    }
}