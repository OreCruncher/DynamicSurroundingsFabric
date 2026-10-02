package org.orecruncher.dsurround.lib.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Reads and writes a configuration field by reflection.
 * <p>
 * A failure here is a programming error (the specification doesn't match the object it is used with), so it is
 * thrown, naming the field, rather than logged and turned into a null.
 */
public class ElementAccessor<T> {

    private final Field field;

    ElementAccessor(Field field) {
        this.field = field;
        this.field.setAccessible(true);
    }

    Field getField() {
        return this.field;
    }

    protected <A extends Annotation> A getAnnotation(Class<A> annotation) {
        return this.field.getAnnotation(annotation);
    }

    @SuppressWarnings("unchecked")
    protected T get(Object instance) {
        try {
            return (T) this.field.get(instance);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException(String.format("Unable to read configuration field '%s' of %s", this.field.getName(), describe(instance)), e);
        }
    }

    protected void set(Object instance, T val) {
        try {
            this.field.set(instance, val);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IllegalStateException(String.format("Unable to set configuration field '%s' of %s to %s", this.field.getName(), describe(instance), val), e);
        }
    }

    private static String describe(Object instance) {
        return instance == null ? "null" : instance.getClass().getName();
    }
}
