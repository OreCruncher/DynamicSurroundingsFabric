package org.orecruncher.dsurround.lib.scripting;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Optional;

public final class ExecutionContext {

    private static final IModLog LOGGER = Client.LOGGER.createChild(ExecutionContext.class);

    private final String contextName;
    private final ScriptEngine engine;
    private final ObjectArray<VariableSet<?>> variables = new ObjectArray<>(8);

    public ExecutionContext(final String contextName) {
        this.contextName = contextName;
        this.engine = ScriptEngineLoader.getEngine();
        this.put("lib", new LibraryFunctions());

        ScriptEngineFactory factory = this.engine.getFactory();
        LOGGER.info("[%s] JavaScript engine: %s (%s)", this.contextName, factory.getEngineName(), factory.getEngineVersion());
    }

    public void put(final String name, @Nullable final Object obj) {
        this.engine.put(name, obj);
    }

    public void add(final VariableSet<?> varSet) {
        if (this.engine.get(varSet.getSetName()) != null)
            throw new IllegalStateException(String.format("Variable set '%s' already defined!", varSet.getSetName()));

        this.variables.add(varSet);
        this.put(varSet.getSetName(), varSet.getInterface());
    }

    public String getName() {
        return this.contextName;
    }

    public void update() {
        this.variables.forEach(VariableSet::update);
    }

    public boolean check(final Script script) {
        final Optional<Object> result = eval(script);
        if (result.isPresent())
            return "true".equalsIgnoreCase(result.toString());
        return false;
    }

    public Optional<Object> eval(final Script script) {
        var cached = script.getCompiledScript();
        var func = cached.orElseGet(() -> {
            var compiled = makeFunction(script.asString());
            script.setCompiledScript(compiled);
            return compiled;
        });

        try {
            final Object result = func.eval();
            return Optional.ofNullable(result);
        } catch (final Throwable t) {
            LOGGER.error(t, "Error execution script: %s", script.asString());
        }

        return Optional.of("ERROR?");
    }

    private CompiledScript makeFunction(final String script) {
        final String source = script + ";";
        try {
            return ((Compilable) this.engine).compile(source);
        } catch (final Throwable t) {
            LOGGER.error(t, "Error compiling script: %s", source);
            return makeErrorFunction(t);
        }
    }

    private CompiledScript makeErrorFunction(Throwable t) {
        String s = String.format("\"%s\"", StringEscapeUtils.escapeJava(t.getMessage()));
        return makeFunction(s);
    }
}