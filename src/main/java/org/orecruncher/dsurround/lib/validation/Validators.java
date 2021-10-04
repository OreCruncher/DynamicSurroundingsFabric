package org.orecruncher.dsurround.lib.validation;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class Validators {

    private static final Reference2ObjectMap<Type, IValidator<?>> registeredValidators = new Reference2ObjectOpenHashMap<>();

    public static void registerValidator(final Type type, final IValidator<?> validator) {
        registeredValidators.put(type, validator);
    }

    public static <T> void validate(final T obj) throws ValidationException {
        validate(obj, (Consumer<String>) null);
    }

    public static <T> void validate(final T obj, @Nullable final Consumer<String> errorLogging) throws ValidationException {
        try {
            if (obj instanceof IValidator) {
                @SuppressWarnings("unchecked") final IValidator<T> v = (IValidator<T>) obj;
                v.validate(obj);
            }
        } catch (final ValidationException ex) {
            if (errorLogging != null) {
                errorLogging.accept(ex.getMessage());
            } else {
                throw ex;
            }
        }
    }

    public static <T> void validate(final T obj, @Nullable final Type type) throws ValidationException {
        validate(obj, type, null);
    }

    public static <T> void validate(final T obj, @Nullable final Type type, @Nullable final Consumer<String> errorLogging) throws ValidationException {
        try {
            if (obj instanceof IValidator) {
                @SuppressWarnings("unchecked") final IValidator<T> v = (IValidator<T>) obj;
                v.validate(obj);
            } else {
                @SuppressWarnings("unchecked") final IValidator<T> validator = (IValidator<T>) registeredValidators.get(type);
                if (validator != null)
                    validator.validate(obj);
            }
        } catch (final ValidationException ex) {
            if (errorLogging != null) {
                errorLogging.accept(ex.getMessage());
            } else {
                throw ex;
            }
        }
    }
}