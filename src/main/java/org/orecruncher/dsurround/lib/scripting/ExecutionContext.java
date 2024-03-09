package org.orecruncher.dsurround.lib.scripting;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import javax.script.*;
import java.util.Optional;

public final class ExecutionContext implements IVariableAccess {

    private final IModLog logger;
    private final String contextName;
    private final ScriptEngine engine;
    private final ObjectArray<VariableSet<?>> variables = new ObjectArray<>(8);

    public ExecutionContext(final String contextName, IModLog logger) {
        this.logger = logger;
        this.contextName = contextName;
        this.engine = ScriptEngineLoader.getEngine().orElseThrow(() -> new RuntimeException("Unable to load a JavaScript engine!"));
        this.put("lib", ContainerManager.resolve(LibraryFunctions.class));

        ScriptEngineFactory factory = this.engine.getFactory();
        this.logger.info("[%s] JavaScript engine: %s (%s)", this.contextName, factory.getEngineName(),
                factory.getEngineVersion());
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
            var compiled = makeFunction(script.asString());
            script.setCompiledScript(compiled);
            return compiled;
        });

        try {
            final Object result = func.eval();
            return Optional.ofNullable(result);
        } catch (final Throwable t) {
            this.logger.error(t, "Error execution script: %s", script.asString());
            return Optional.of("ERROR? " + t.getMessage());
        }
    }

    private CompiledScript makeFunction(final String script) {
        final String source = script + ";";
        try {
            return ((Compilable) this.engine).compile(source);
        } catch (final Throwable t) {
            this.logger.error(t, "Error compiling script: %s", source);
            return makeErrorFunction(t);
        }
    }

    private CompiledScript makeErrorFunction(Throwable t) {
        String s = String.format("\"%s\"", StringEscapeUtils.escapeJava(t.getMessage()));
        return makeFunction(s);
    }
}