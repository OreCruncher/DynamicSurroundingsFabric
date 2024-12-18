package org.orecruncher.dsurround.lib.config;

public class Binder<T> {

    private final ConfigElement.PropertyValue<T> property;
    private final T instance;

    @SuppressWarnings("unchecked")
    Binder(ConfigElement.PropertyValue<?> property, Object instance) {
        this.property = (ConfigElement.PropertyValue<T>) property;
        this.instance = (T)instance;
    }

    public void setValue(T value) {
        this.property.setValue(this.instance, value);
    }

    public T getValue() {
        return this.property.getValue(this.instance);
    }

    public T defaultValue() {
        return this.property.defaultValue();
    }

}