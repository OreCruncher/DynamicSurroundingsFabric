package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class ConfigValue<T> {

    private final Object instanceReference;
    private final Field field;

    ConfigValue(Object instance, Field field) {
        this.instanceReference = instance;
        this.field = field;

        this.field.setAccessible(true);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.field.getAnnotation(annotation);
    }

    public String getPropertyName() {
        return this.field.getName();
    }

    @SuppressWarnings("unchecked")
    public T get() {
        try {
            return (T) this.field.get(this.instanceReference);
        } catch (Throwable ignore)
        {}
        return null;
    }

    public void set(T val) {
        try {
            this.field.set(this.instanceReference, val);
        } catch (Throwable ignore)
        {}
    }
}
