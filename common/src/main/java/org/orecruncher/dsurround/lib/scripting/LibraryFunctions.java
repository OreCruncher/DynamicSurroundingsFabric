package org.orecruncher.dsurround.lib.scripting;

import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;
import org.orecruncher.dsurround.lib.scripting.engine.expression.Conditional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Core functions that are automatically defined for the Script Engine when it is initialized
 */
public final class LibraryFunctions {

    // Patterns built at runtime are cached as well. Bounded so that dynamically built patterns cannot grow the
    // cache without limit. (Constant patterns are converted once at compile time and do not use the cache.)
    private static final int MAX_CACHED_PATTERNS = 256;
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * A regular expression. A constant pattern is compiled once, when the script is compiled, and an invalid
     * pattern is reported as a compile error at the argument.
     */
    public static final ArgType<Pattern> REGEX = ArgType.of("regular expression", LibraryFunctions::toPattern);

    public static void configure(IConfigureDefinition config) {
        // Only the selected branch is evaluated. Compiled to a dedicated node so that it does not allocate; a
        // constant condition selects the branch at compile time.
        config.function("lib.iif")
                .param(ArgType.BOOLEAN).param(ArgType.ANY).param(ArgType.ANY)
                .pure()
                .lazy()
                .compileWith(Conditional::compile)
                .handler(args -> args.bool(0) ? args.value(1) : args.value(2));

        config.function("lib.match")
                .param(REGEX).param(ArgType.STRING)
                .pure()
                .handler(args -> args.<Pattern>get(0).matcher(args.string(1)).matches());

        config.function("lib.oneOf")
                .param(ArgType.ANY).param(ArgType.ANY).varParams(ArgType.ANY)
                .pure()
                .handler(LibraryFunctions::oneOf);

        config.function("lib.isBetween")
                .param(ArgType.NUMBER).param(ArgType.NUMBER).param(ArgType.NUMBER)
                .pure()
                .handler(args -> {
                    var value = args.number(0);
                    return value >= args.number(1) && value <= args.number(2);
                });
    }

    private static boolean oneOf(ScriptArguments args) {
        var testee = args.value(0);
        for (int i = 1; i < args.count(); i++)
            if (ScriptHelpers.isEqual(testee, args.value(i)))
                return true;
        return false;
    }

    private static Pattern toPattern(Object value) {
        if (value instanceof Pattern p)
            return p;
        var regex = ScriptHelpers.toStringValue(value);
        var pattern = PATTERN_CACHE.get(regex);
        if (pattern == null) {
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                return ArgType.reject("invalid regular expression '%s' (%s)".formatted(regex, e.getDescription()));
            }
            if (PATTERN_CACHE.size() < MAX_CACHED_PATTERNS)
                PATTERN_CACHE.put(regex, pattern);
        }
        return pattern;
    }
}
