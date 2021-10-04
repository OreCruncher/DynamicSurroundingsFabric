package org.orecruncher.dsurround.lib.validation;

import org.jetbrains.annotations.Nullable;

public class ValidationException extends Exception {

    public ValidationException(final String field, final String fmt, @Nullable final Object... params) {
        super(String.format("%s: %s", field, String.format(fmt, params)));
    }
}