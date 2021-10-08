package org.orecruncher.dsurround.lib.validation;

import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class ValidationHelpers {

    private static final IModLog LOGGER = Client.LOGGER.createChild(ValidationHelpers.class);

    private static final Pattern COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

    public static void notNull(final String field, @Nullable final Object value, @Nullable final IModLog errorLogger) throws ValidationException {
        if (value != null)
            return;
        handleException(new ValidationException(field, "Must not be null"), errorLogger);
    }

    public static <T> void hasElements(final String field, @Nullable final List<T> elements, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, elements, errorLogger);
        if (elements != null) {
            if (elements.size() > 0)
                return;
            handleException(new ValidationException(field, "Has no elements"), errorLogger);
        }
    }

    public static <T> void hasElements(final String field, @Nullable final T[] elements, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, elements, errorLogger);
        if (elements != null) {
            if (elements.length > 0)
                return;
            handleException(new ValidationException(field, "Has no elements"), errorLogger);
        }
    }

    public static void isOneOf(final String field, @Nullable final String value, final boolean ignoreCase, final String[] values, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        hasElements(field, values, errorLogger);
        if (value != null) {
            final Function<String, Integer> check = ignoreCase ? value::compareToIgnoreCase : value::compareTo;
            for (final String s : values)
                if (check.apply(s) == 0)
                    return;
            final String possibles = String.join(",", values);
            handleException(new ValidationException(field, "Invalid value \"%s\"; must be one of \"%s\"", value, possibles), errorLogger);
        }
    }

    public static void inRange(final String field, final int value, final int min, final int max, @Nullable final IModLog errorLogger) throws ValidationException {
        if (value >= min && value <= max)
            return;
        handleException(new ValidationException(field, "Invalid value \"%s\"; must be between %d and %d inclusive", value, min, max), errorLogger);
    }

    public static void inRange(final String field, final float value, final float min, final float max, @Nullable final IModLog errorLogger) throws ValidationException {
        if (value >= min && value <= max)
            return;
        handleException(new ValidationException(field, "Invalid value \"%s\"; must be between %f and %f inclusive", value, min, max), errorLogger);
    }

    public static void inRange(final String field, final double value, final double min, final double max, @Nullable final IModLog errorLogger) throws ValidationException {
        if (value >= min && value <= max)
            return;
        handleException(new ValidationException(field, "Invalid value \"%s\"; must be between %f and %f inclusive", value, min, max), errorLogger);
    }

    public static void notNullOrEmpty(final String field, final String value, @Nullable final IModLog errorLogger) throws ValidationException {
        if (StringUtils.isNotEmpty(value))
            return;
        handleException(new ValidationException(field, "Must not be null or empty"), errorLogger);
    }

    public static void notNullOrWhitespace(final String field, final String value, @Nullable final IModLog errorLogger) throws ValidationException {
        if (StringUtils.isNotBlank(value))
            return;
        handleException(new ValidationException(field, "Must not be null or empty, or comprised of whitespace"), errorLogger);
    }

    public static <T extends Enum<T>> void isEnumValue(final String field, @Nullable final String value, final Class<T> enumeration, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        if (value != null) {
            final String[] possibles = Arrays.stream(enumeration.getEnumConstants()).map(Enum::name).toArray(String[]::new);
            isOneOf(field, value, true, possibles, errorLogger);
        }
    }

    public static void matchRegex(final String field, @Nullable final String value, final String regex, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        if (value != null) {
            if (Pattern.compile(regex).matcher(value).matches())
                return;
            handleException(new ValidationException(field, "Does \"%s\" not match regular expression \"%s\"", value, regex), errorLogger);
        }
    }

    public static void mustBeLowerCase(final String field, @Nullable final String value, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        if (value != null) {
            final String lower = value.toLowerCase(Locale.ROOT);
            if (lower.equals(value))
                return;
            throw new ValidationException(field, "\"%s\" Must be in lower case", value);
        }
    }

    public static void isProperResourceLocation(final String field, @Nullable final String value, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        if (value != null)
            if (!Identifier.isValid(value))
                handleException(new ValidationException(field, "\"%s\" is not a proper resource location", value), errorLogger);
    }

    public static void isValidColorCode(final String field, @Nullable final String value, @Nullable final IModLog errorLogger) throws ValidationException {
        notNull(field, value, errorLogger);
        if (value != null) {
            if (COLOR_PATTERN.matcher(value).matches())
                return;
            handleException(new ValidationException(field, "\"%s\" is not a valid color code.  Must be a valid HTML hexadecimal color code.", value), errorLogger);
        }
    }

    private static void handleException(final ValidationException ex, @Nullable final IModLog errorLogger) throws ValidationException {
        var logger = errorLogger != null ? errorLogger : LOGGER;
        logger.error(ex, ex.getMessage());
        if (errorLogger == null)
            throw ex;
    }
}