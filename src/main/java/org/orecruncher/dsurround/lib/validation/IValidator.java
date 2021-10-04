package org.orecruncher.dsurround.lib.validation;

public interface IValidator<T> {
    void validate(final T obj) throws ValidationException;
}
