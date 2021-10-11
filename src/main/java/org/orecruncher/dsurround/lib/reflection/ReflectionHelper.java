package org.orecruncher.dsurround.lib.reflection;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused")
public final class ReflectionHelper {
    private ReflectionHelper() {
    }

    @Nullable
    public static Field resolveField( final String className,  final String... fieldName) {
        Preconditions.checkNotNull(className);
        Preconditions.checkArgument(fieldName.length > 0, "Field name cannot be empty");
        try {
            return resolveField(Class.forName(className), fieldName);
        } catch ( final Throwable ignored) {
        }
        return null;
    }

    @Nullable
    public static Field resolveField( final Class<?> clazz,  final String... fieldName) {
        Preconditions.checkNotNull(clazz);
        Preconditions.checkArgument(fieldName.length > 0, "Field name cannot be empty");
        for (final String name : fieldName) {
            try {
                final Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    public static Method resolveMethod(final String className, final String[] names, Class<?>... parameters) {
        try {
            return resolveMethod(Class.forName(className), names, parameters);
        } catch ( final Throwable ignored) {
        }
        return null;
    }

    @Nullable
    public static Method resolveMethod( final Class<?> clazz,  final String[] names, Class<?>... parameters) {

        for (final String name : names) {
            try {
                final Method f = clazz.getDeclaredMethod(name, parameters);
                f.setAccessible(true);
                return f;
            } catch ( final Throwable ignored) {
            }
        }
        return null;
    }

    @Nullable
    public static Class<?> resolveClass( final String className) {
        Preconditions.checkNotNull(className);
        try {
            return Class.forName(className);
        } catch ( final Throwable ignored) {
        }
        return null;
    }

    
    public static Collection<Field> getStaticFields(final Class<?> clazz) {
        final List<Field> staticFields = new ArrayList<>();
        final Field[] declaredFields = clazz.getDeclaredFields();
        for (final Field field : declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                staticFields.add(field);
            }
        }

        return staticFields;
    }

}