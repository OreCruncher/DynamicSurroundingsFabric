package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class ElementAccessor<T> {

    private final Field field;

    ElementAccessor(Field field) {
        this.field = field;

        this.field.setAccessible(true);
    }

    protected <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.field.getAnnotation(annotation);
    }

    @SuppressWarnings("unchecked")
    protected T get(Object instance) {
        try {
            return (T) this.field.get(instance);
        } catch (Throwable ignore) {
        }
        return null;
    }

    protected void set(Object instance, T val) {
        try {
            this.field.set(instance, val);
        } catch (Throwable ignore) {
        }
    }
}
