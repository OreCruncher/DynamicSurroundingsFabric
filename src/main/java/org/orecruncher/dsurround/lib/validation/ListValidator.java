package org.orecruncher.dsurround.lib.validation;

import java.util.List;

@SuppressWarnings("unused")
public class ListValidator<V extends IValidator<V>> implements IValidator<List<V>> {
    @Override
    public void validate(final List<V> obj) throws ValidationException {
        for (final V entry : obj)
            entry.validate(entry);
    }
}