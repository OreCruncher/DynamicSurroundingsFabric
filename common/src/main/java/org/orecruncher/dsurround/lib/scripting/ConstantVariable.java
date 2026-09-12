package org.orecruncher.dsurround.lib.scripting;

public class ConstantVariable<T> implements IScriptVariable {

    final T value;

    ConstantVariable(T value) {
        this.value = value;
    }

    @Override
    public Object getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "Constant %s".formatted(this.value.toString());
    }

    public static <T> ConstantVariable<T> of(T value) {
        return new ConstantVariable<>(value);
    }

    public static ConstantVariable<Boolean> TRUE = of(Boolean.TRUE);
    public static ConstantVariable<Boolean> FALSE = of(Boolean.FALSE);
}
