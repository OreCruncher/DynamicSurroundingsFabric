package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Feeds the parser large numbers of random documents and checks properties that must hold for every input, rather
 * than the outcome for any one input.
 * <p>
 * The parser promises never to reject input, so the first property is simply "never throws". The others describe the
 * shape of the output: fully merged, well-formed links, no leaked state, and so on.
 * <p>
 * Runs are reproducible. Case {@code i} always uses {@code new Random(seed + i)}. When a case fails, the input is
 * shrunk to a minimal one that still fails and is printed as a Java string literal, ready to paste into a regression
 * test in {@link MarkdownParserTests}. To explore further, run with a different seed or many more cases:
 * {@code -Dmarkdown.fuzz.seed=123 -Dmarkdown.fuzz.cases=1000000} (the build must pass system properties through to the
 * test JVM).
 * <p>
 * Not covered: run time. Very long inputs with many unterminated links are known to be slower than linear, and a
 * timing assertion would be flaky.
 * Expected location: src/test/java/org/orecruncher/dsurround/lib/markdown/
 */
class MarkdownParserFuzzTests {

    private static final int CASES = Integer.getInteger("markdown.fuzz.cases", 20_000);
    private static final long SEED = Long.getLong("markdown.fuzz.seed", 20260930L);

    /**
     * Markup-heavy building blocks, including malformed and hostile ones.
     */
    private static final String[] FRAGMENTS = {
            "*", "**", "***", "_", "__", "___", "~", "~~",
            "[", "](", ")", "]",
            "<color:red>", "<color:#FF0000>", "<color:bad>", "<color:", "<color:#fff>", "</color>", "<COLOR:RED>",
            "# ", "## ", "####### ", "#", "> ", ">", "- ", "* ", "-", "1. ",
            "\n", "\n\n", "\r\n", "\r", " ", "  ", "\t",
            "a", "b", "word", "2", "é", "\uD83D\uDE00", "\uD83D", "\u2028",
            "\\", "\\*", "\\\n",
            "[t](https://e.com)", "[**b** c](https://x.org/p)", "[](https://e.com)", "[a](https://e.com/(x))",
            "[x](javascript:1)", "[x](javascript://e.com/%0Aalert(1))", "[x](ftp://e.com/f)", "[x](file://host/p)",
            "[x](HTTPS://E.COM/Up)", "[x](https://", "[x](http://a b)", "![i](https://e.com/i.png)",
            "https://e.com", "%s", "`", "!"
    };

    /**
     * Printable ASCII plus awkward whitespace and non-BMP characters.
     */
    private static final String NOISE = buildNoise();

    /**
     * Every character that has meaning to the parser, plus a few neutral and non-BMP ones. No line breaks.
     */
    private static final String SPECIALS = "\\*_~[]()<>#-!`.:/ aA1%\t\u00e9\uD83D\uDE00";

    /**
     * Characters that mean nothing to the parser anywhere in a line.
     */
    private static final String PLAIN = "abcXYZ019 .,;:!?'\"%&+/@$=^|{}";

    private static final Options[] OPTIONS = {
            Options.DEFAULT,
            Options.UNIFORM,
            Options.builder().quoteItalic(false).textColor("white").build(),
            Options.builder().headingColor("").bulletColor("").quoteColor("").linkColor("")
                    .bulletStyle("").quoteStyle("").build(),
            Options.builder().linkHoverTemplate("100% of %s").linkHoverTranslationKey("").build()
    };

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- The tests -------------------------------------------------------------------------------------------

    /**
     * Returns null if the parser output is well formed for this input, otherwise a description of the problem.
     */
    private static String checkInvariants(String markdown, Options options) {
        try {
            List<Segment> segments = MarkdownParser.parseToSegments(markdown, options);

            if (!segments.equals(MarkdownParser.parseToSegments(markdown, options))) {
                return "output differs between two runs";
            }
            if (!Optimizer.optimize(segments).equals(segments)) {
                return "output is not fully merged (optimizing it again changes it)";
            }

            for (int i = 0; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                Style style = segment.style();

                if (segment.text().isEmpty()) {
                    return "empty segment at " + i;
                }
                if (segment.text().indexOf('\r') >= 0) {
                    return "carriage return in segment " + i + " " + show(segment.text());
                }
                if (!Objects.equals(style.font(), options.font())) {
                    return "wrong font in segment " + i;
                }
                for (Boolean flag : new Boolean[]{style.bold(), style.italic(), style.underline(), style.strikethrough()}) {
                    if (flag != null && !flag) {
                        return "a style flag is false instead of unset in segment " + i;
                    }
                }

                boolean isLink = style.clickEventUrl() != null;
                if (isLink == (style.hoverEventText() == null)) {
                    return "click and hover text disagree in segment " + i;
                }
                if (isLink && !isAcceptableUrl(style.clickEventUrl())) {
                    return "link with an unacceptable url " + show(style.clickEventUrl()) + " in segment " + i;
                }
                if (isLink && style.underline() == null) {
                    return "link is not underlined in segment " + i;
                }

                if (i > 0) {
                    Segment previous = segments.get(i - 1);
                    boolean newline = previous.text().indexOf('\n') >= 0 || segment.text().indexOf('\n') >= 0;
                    if (!newline && previous.style().equals(style)) {
                        return "segments " + (i - 1) + " and " + i + " have the same style and were not merged";
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return "threw " + t;
        }
    }

    /**
     * The component built from the segments must carry exactly the same text, one child per segment.
     */
    private static String checkExport(String markdown, Options options) {
        try {
            List<Segment> segments = MarkdownParser.parseToSegments(markdown, options);
            Optional<Component> component = MarkdownParser.markdownToComponent(markdown, options);
            if (component.isEmpty()) {
                return "no component for non-null input";
            }
            if (!component.get().getString().equals(plain(segments))) {
                return "component text " + show(component.get().getString()) + " differs from segment text";
            }
            if (component.get().getSiblings().size() != segments.size()) {
                return "component has " + component.get().getSiblings().size() + " children for "
                        + segments.size() + " segments";
            }
            return null;
        } catch (Throwable t) {
            return "threw " + t;
        }
    }

    private static boolean isAcceptableUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            return new URI(url).getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Runs {@code cases} random cases. On the first failure the input is shrunk and the test fails with it.
     */
    private static void fuzz(int cases, Function<Random, String> generator, BiFunction<String, Options, String> property) {
        for (int i = 0; i < cases; i++) {
            Random random = new Random(SEED + i);
            String markdown = generator.apply(random);
            int optionsIndex = random.nextInt(OPTIONS.length);
            Options options = OPTIONS[optionsIndex];

            String problem = safely(property, markdown, options);
            if (problem != null) {
                String minimal = shrink(markdown, candidate -> safely(property, candidate, options) != null);
                fail("case %d (seed %d, options #%d): %s%n  minimal input: %s%n  minimal problem: %s%n  original input: %s"
                        .formatted(i, SEED, optionsIndex, problem, show(minimal), safely(property, minimal, options),
                                show(markdown)));
            }
        }
    }

    private static String safely(BiFunction<String, Options, String> property, String markdown, Options options) {
        try {
            return property.apply(markdown, options);
        } catch (Throwable t) {
            return "threw " + t;
        }
    }

    /**
     * Greedy shrinking: repeatedly deletes chunks (large first, down to single characters) as long as the input keeps
     * failing. The result is small, but not guaranteed to be the smallest possible.
     */
    private static String shrink(String input, Predicate<String> stillFails) {
        String current = input;
        int chunk = Math.max(1, current.length() / 2);
        while (true) {
            boolean removed = false;
            for (int start = 0; start + chunk <= current.length(); ) {
                String candidate = current.substring(0, start) + current.substring(start + chunk);
                if (stillFails.test(candidate)) {
                    current = candidate;
                    removed = true;
                } else {
                    start += chunk;
                }
            }
            if (!removed) {
                if (chunk == 1) {
                    return current;
                }
                chunk = Math.max(1, chunk / 2);
            }
        }
    }

    // ---- Properties ------------------------------------------------------------------------------------------

    private static String randomDocument(Random random) {
        StringBuilder sb = new StringBuilder();
        if (random.nextInt(10) < 7) {
            int count = 1 + random.nextInt(random.nextInt(10) == 0 ? 80 : 25);
            for (int i = 0; i < count; i++) {
                sb.append(FRAGMENTS[random.nextInt(FRAGMENTS.length)]);
            }
        } else {
            int count = 1 + random.nextInt(60);
            for (int i = 0; i < count; i++) {
                sb.append(NOISE.charAt(random.nextInt(NOISE.length())));
            }
        }
        return sb.toString();
    }

    private static String randomFrom(Random random, String alphabet, int maxLength) {
        int count = random.nextInt(maxLength + 1);
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Puts a backslash in front of every character, which the parser must treat as "this character is literal".
     */
    private static String escapeAll(String text) {
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            sb.append('\\').append(text.charAt(i));
        }
        return sb.toString();
    }

    // ---- The fuzzing machinery -------------------------------------------------------------------------------

    private static String plain(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            sb.append(segment.text());
        }
        return sb.toString();
    }

    /**
     * Formats text as a Java string literal, so a failing input can be pasted straight into a test.
     */
    private static String show(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String buildNoise() {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c <= 126; c++) {
            sb.append(c);
        }
        return sb.append("\n\r\t\u0000\u00e9\u2028\uD83D\uDE00").toString();
    }

    // ---- Generators and helpers ------------------------------------------------------------------------------

    @Test
    void randomDocumentsSatisfyTheParserInvariants() {
        fuzz(CASES, MarkdownParserFuzzTests::randomDocument, MarkdownParserFuzzTests::checkInvariants);
    }

    @Test
    void randomDocumentsExportToComponentsWithTheSameText() {
        fuzz(CASES / 4, MarkdownParserFuzzTests::randomDocument, MarkdownParserFuzzTests::checkExport);
    }

    @Test
    void escapingEveryCharacterAlwaysGivesLiteralText() {
        fuzz(CASES, r -> randomFrom(r, SPECIALS, 40), (markdown, options) -> {
            String escaped = escapeAll(markdown);
            String actual = plain(MarkdownParser.parseToSegments(escaped, options));
            return actual.equals(markdown) ? null : "escaped input " + show(escaped) + " rendered as " + show(actual);
        });
    }

    @Test
    void textWithNoMarkupIsPreservedExactly() {
        fuzz(CASES, r -> randomFrom(r, PLAIN, 60), (markdown, options) -> {
            List<Segment> segments = MarkdownParser.parseToSegments(markdown, options);
            if (!plain(segments).equals(markdown)) {
                return "rendered as " + show(plain(segments));
            }
            return segments.size() <= 1 ? null : "split into " + segments.size() + " segments";
        });
    }

    @Test
    void inlineStateNeverLeaksIntoALaterParagraph() {
        fuzz(CASES, r -> randomDocument(r) + "\n\nTAILWORD", (markdown, options) -> {
            List<Segment> segments = MarkdownParser.parseToSegments(markdown, options);
            if (segments.isEmpty()) {
                return "no segments";
            }
            Segment last = segments.get(segments.size() - 1);
            if (!last.text().equals("TAILWORD")) {
                return "last segment is " + show(last.text());
            }
            Style s = last.style();
            if (s.bold() != null || s.italic() != null || s.underline() != null || s.strikethrough() != null
                    || s.clickEventUrl() != null || s.hoverEventText() != null) {
                return "styling leaked into the next paragraph: " + s;
            }
            return null;
        });
    }

    @Test
    void shrinkFindsTheSmallestFailingInput() {
        assertEquals("AB", shrink("xxAyyBzz-AB-qq", s -> s.contains("AB")));
        assertEquals("", shrink("anything", s -> true));
        assertEquals("q", shrink("abcqdef", s -> s.contains("q")));
    }
}