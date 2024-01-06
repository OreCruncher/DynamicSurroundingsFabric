package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class ConfigValue<T> {

    private final Field field;

    ConfigValue(Field field) {
        this.field = field;

        this.field.setAccessible(true);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.field.getAnnotation(annotation);
    }

    @SuppressWarnings("unchecked")
    public T get(Object instance) {
        try {
            return (T) this.field.get(instance);
        } catch (Throwable ignore) {
        }
        return null;
    }

    public void set(Object instance, T val) {
        try {
            this.field.set(instance, val);
        } catch (Throwable ignore) {
        }
    }
}
