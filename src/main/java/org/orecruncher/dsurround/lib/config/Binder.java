package org.orecruncher.dsurround.lib.config;

import dev.isxander.yacl3.api.Binding;

public class Binder<T> implements Binding<T> {

    private final ConfigElement.PropertyValue<T> property;
    private final T instance;

    @SuppressWarnings("unchecked")
    Binder(ConfigElement.PropertyValue<?> property, Object instance) {
        this.property = (ConfigElement.PropertyValue<T>) property;
        this.instance = (T)instance;
    }

    @Override
    public void setValue(T value) {
        this.property.setValue(this.instance, value);
    }

    @Override
    public T getValue() {
        return this.property.getValue(this.instance);
    }

    @Override
    public T defaultValue() {
        return this.property.defaultValue();
    }

}
