package org.orecruncher.dsurround.lib.scripting;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

public record ConstantVariable<T>(T value) implements IScriptVariable {

    @Override
    public Object getValue() {
        return this.value;
    }

    @Override
    public @NotNull String toString() {
        return "Constant %s".formatted(this.value.toString());
    }

    public static <T> ConstantVariable<T> of(T value) {
        Preconditions.checkNotNull(value);
        return new ConstantVariable<>(value);
    }

    public static ConstantVariable<Boolean> TRUE = of(Boolean.TRUE);
    public static ConstantVariable<Boolean> FALSE = of(Boolean.FALSE);
}
