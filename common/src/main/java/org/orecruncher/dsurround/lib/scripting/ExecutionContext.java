package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Optional;

public final class ExecutionContext implements IVariableAccess {

    private final IModLog logger;
    private final String contextName;
    private final SimpleExpressionEvaluator evaluator;
    private final ObjectArray<VariableSet<?>> variables = new ObjectArray<>(8);

    public ExecutionContext(final String contextName, IModLog logger) {
        this.logger = logger;
        this.contextName = contextName;
        this.evaluator = new SimpleExpressionEvaluator();
        this.put("lib", ContainerManager.resolve(LibraryFunctions.class));

        this.logger.info("[%s] Expression engine: Dynamic Surroundings safe evaluator", this.contextName);
    }

    @Override
    public void put(final String name, @Nullable final Object obj) {
        this.evaluator.put(name, obj);
    }

    public void add(final VariableSet<?> varSet) {
        if (this.evaluator.contains(varSet.getSetName()))
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
        return result.map(SimpleExpressionEvaluator::asBoolean).orElse(false);
    }

    public Optional<Object> eval(final Script script) {
        try {
            final Object result = this.evaluator.eval(script.asString());
            return Optional.ofNullable(result);
        } catch (final Throwable t) {
            this.logger.error(t, "Error evaluating expression: %s", script.asString());
            return Optional.of(false);
        }
    }
}
