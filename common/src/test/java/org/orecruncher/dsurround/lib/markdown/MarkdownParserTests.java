package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the markdown pipeline (Lexer -> SegmentBuilder -> Optimizer) and Options.
 * <p>
 * These assert against the Segment tree produced by {@code MarkdownParser.parseToTree}, so they check parsing
 * and styling without needing Minecraft's Component classes. Conversion to real Components is covered by
 * {@code ComponentExporterTest}. Lives in the same package to reach package-private types.
 * Expected location: src/test/java/org/orecruncher/dsurround/lib/markdown/
 */
class MarkdownParserTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------

    private static List<Segment> parse(String markdown) {
        return parse(markdown, Options.DEFAULT);
    }

    private static List<Segment> parse(String markdown, Options options) {
        return MarkdownParser.parseToSegments(markdown, options);
    }

    /**
     * The expected color, parsed straight from Minecraft so a bug in Colors can't hide itself.
     */
    private static TextColor color(String value) {
        return TextColor.parseColor(value).result().orElseThrow();
    }

    private static String plain(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    /**
     * First segment whose text contains the fragment. Fails with a dump of the list if there is none.
     */
    private static Segment find(List<Segment> segments, String fragment) {
        for (Segment s : segments) {
            if (s.text().contains(fragment)) {
                return s;
            }
        }
        throw new AssertionError("No segment containing '" + fragment + "' in: " + describe(segments));
    }

    private static String describe(List<Segment> segments) {
        StringBuilder sb = new StringBuilder("[");
        for (Segment s : segments) {
            sb.append('\'').append(s.text().replace("\n", "\\n")).append("' ");
        }
        return sb.append(']').toString();
    }

    private static void assertNoLinks(List<Segment> segments) {
        for (Segment s : segments) {
            assertNull(s.style().clickEventUrl(), "unexpected link on '" + s.text() + "'");
        }
    }

    // ---- Plain text and inline styles ------------------------------------------------------------------------

    @Test
    void plainTextHasNoStyling() {
        List<Segment> segments = parse("hello world");

        assertEquals(1, segments.size());
        Segment n = segments.get(0);
        assertEquals("hello world", n.text());
        assertNull(n.style().bold());
        assertNull(n.style().italic());
        assertNull(n.style().underline());
        assertNull(n.style().strikethrough());
        assertNull(n.style().color());
    }

    @Test
    void boldAppliesOnlyBetweenMarkers() {
        List<Segment> segments = parse("one **two** three");

        assertEquals(Boolean.TRUE, find(segments, "two").style().bold());
        assertNull(find(segments, "one").style().bold());
        assertNull(find(segments, "three").style().bold());
    }

    @Test
    void italicUnderlineAndStrikethrough() {
        assertEquals(Boolean.TRUE, find(parse("a *it* b"), "it").style().italic());
        assertEquals(Boolean.TRUE, find(parse("a __ul__ b"), "ul").style().underline());
        assertEquals(Boolean.TRUE, find(parse("a ~~gone~~ b"), "gone").style().strikethrough());
    }

    @Test
    void escapedMarkersAreLiteralAndMerged() {
        List<Segment> segments = parse("a\\*b");

        assertEquals(1, segments.size());
        assertEquals("a*b", segments.get(0).text());
    }

    @Test
    void unclosedMarkerDoesNotLeakIntoNextParagraph() {
        // "*3" can open emphasis and is never closed, so it stays open only until the end of the paragraph
        List<Segment> segments = parse("2 *3\n\nnext");

        assertEquals(Boolean.TRUE, find(segments, "3").style().italic());
        assertNull(find(segments, "next").style().italic());
    }

    // ---- Markers surrounded by whitespace --------------------------------------------------------------------

    @Test
    void asteriskWithSpacesOnBothSidesIsLiteral() {
        List<Segment> segments = parse("2 * 3 = 6");

        assertEquals("2 * 3 = 6", plain(segments));
        assertEquals(1, segments.size());
        assertNull(segments.get(0).style().italic());
    }

    @Test
    void spacedMarkerDoesNotAffectLaterEmphasis() {
        List<Segment> segments = parse("2 * 3 and *real* italic");

        assertEquals("2 * 3 and real italic", plain(segments));
        assertEquals(Boolean.TRUE, find(segments, "real").style().italic());
        assertNull(find(segments, "2 * 3").style().italic());
        assertNull(find(segments, "italic").style().italic());
    }

    @Test
    void spacedDoubleMarkersAreLiteralForEveryInlineStyle() {
        for (String marker : new String[]{"**", "__", "~~"}) {
            List<Segment> segments = parse("a " + marker + " b");

            assertEquals("a " + marker + " b", plain(segments));
            Segment n = segments.get(0);
            assertNull(n.style().bold(), marker);
            assertNull(n.style().underline(), marker);
            assertNull(n.style().strikethrough(), marker);
        }
    }

    @Test
    void markerAtEndOfLineAfterASpaceIsLiteral() {
        assertEquals("a * b", plain(parse("a *\nb")));
    }

    @Test
    void spacedMarkerInsideHeadingIsLiteral() {
        List<Segment> segments = parse("# 2 * 3");

        assertEquals("2 * 3", plain(segments));
        assertNull(segments.get(0).style().italic());
    }

    @Test
    void spacedMarkerStillClosesAnOpenStyle() {
        List<Segment> segments = parse("**bold ** text");

        assertEquals(Boolean.TRUE, find(segments, "bold").style().bold());
        assertNull(find(segments, " text").style().bold());
    }

    @Test
    void markersTouchingTextStillWork() {
        assertEquals(Boolean.TRUE, find(parse("a*b*c"), "b").style().italic());   // no spaces at all
        assertEquals(Boolean.TRUE, find(parse("*a*"), "a").style().italic());     // whole input
        assertEquals(Boolean.TRUE, find(parse("**a**"), "a").style().bold());
        assertEquals(Boolean.TRUE, find(parse("x **a**"), "a").style().bold());   // space before opener only
        assertEquals(Boolean.TRUE, find(parse("**a** y"), "a").style().bold());   // space after closer only
    }

    // ---- Colors ----------------------------------------------------------------------------------------------

    @Test
    void colorDoesNotLeakPastClosingTag() {
        // Regression: the old style stack popped the wrong entry, leaving " after" red and bold.
        List<Segment> segments = parse("<color:red>**x**</color> after");

        Segment x = find(segments, "x");
        assertEquals(color("red"), x.style().color());
        assertEquals(Boolean.TRUE, x.style().bold());

        Segment after = find(segments, "after");
        assertNull(after.style().color());
        assertNull(after.style().bold());
    }

    @Test
    void nestedColorsRestoreOuterColor() {
        List<Segment> segments = parse("<color:red>a<color:blue>b</color>c</color>d");

        assertEquals(color("red"), find(segments, "a").style().color());
        assertEquals(color("blue"), find(segments, "b").style().color());
        assertEquals(color("red"), find(segments, "c").style().color());
        assertNull(find(segments, "d").style().color());
    }

    @Test
    void colorTagsAreCaseInsensitive() {
        List<Segment> segments = parse("<COLOR:RED>x</COLOR>y");

        assertEquals(color("red"), find(segments, "x").style().color());
        assertNull(find(segments, "y").style().color());
    }

    @Test
    void hexColorsAreAccepted() {
        assertEquals(color("#FF00aa"), find(parse("<color:#FF00aa>x</color>"), "x").style().color());
    }

    @Test
    void hexColorCaseDoesNotMatter() {
        assertEquals(color("#ff00aa"), find(parse("<color:#FF00AA>x</color>"), "x").style().color());

        // The Optimizer merges neighbours using Style equality, so equal colors must compare equal
        List<Segment> segments = parse("<color:#FF00AA>a</color><color:#ff00aa>b</color>");
        assertEquals(1, segments.size());
        assertEquals("ab", segments.get(0).text());
    }

    @Test
    void shortSignedAndOversizedHexAreShownLiterally() {
        for (String tag : new String[]{"#fff", "#+fffff", "#-fffff", "#1234567", "#gg0000", "#"}) {
            List<Segment> segments = parse("<color:" + tag + ">x</color>");

            assertEquals("<color:" + tag + ">x</color>", plain(segments), tag);
            assertNull(find(segments, "x").style().color(), tag);
        }
    }

    @Test
    void colorInsideBoldKeepsBoldAndColorOnTheSameSegment() {
        List<Segment> segments = parse("**<color:#FF0000>Red Bold Text</color>**");

        assertEquals(1, segments.size());
        Segment n = segments.get(0);
        assertEquals("Red Bold Text", n.text());
        assertEquals(color("#FF0000"), n.style().color());
        assertEquals(Boolean.TRUE, n.style().bold());
    }

    @Test
    void unknownColorIsShownLiterally() {
        List<Segment> segments = parse("<color:foo>x</color>");

        assertEquals("<color:foo>x</color>", plain(segments));
        assertNull(find(segments, "x").style().color());
    }

    @Test
    void unknownNestedColorDoesNotCloseTheOuterColor() {
        List<Segment> segments = parse("<color:red>a <color:bogus>b</color> c</color> d");

        assertEquals("a <color:bogus>b</color> c d", plain(segments));
        assertEquals(color("red"), find(segments, "b").style().color());
        assertEquals(color("red"), find(segments, " c").style().color());
        assertNull(find(segments, " d").style().color());
    }

    @Test
    void unclosedColorTagKeepsItsAngleBracket() {
        assertEquals("a <color:red b", plain(parse("a <color:red b")));
    }

    @Test
    void colorTagDoesNotSearchAcrossLines() {
        // Previously the '>' on the next line would have been treated as the end of the tag.
        List<Segment> segments = parse("<color:red\nfoo>");

        assertEquals("<color:red foo>", plain(segments));
        for (Segment n : segments) {
            assertNull(n.style().color());
        }
    }

    @Test
    void strayClosingColorTagIsDropped() {
        assertEquals("ab", plain(parse("a</color>b")));
    }

    // ---- Links -----------------------------------------------------------------------------------------------

    @Test
    void wellFormedLink() {
        List<Segment> segments = parse("[site](https://example.com) tail");

        Segment link = find(segments, "site");
        assertEquals("https://example.com", link.style().clickEventUrl());
        assertEquals(Boolean.TRUE, link.style().underline());
        assertEquals(Options.DEFAULT.linkColor(), link.style().color());

        assertNull(find(segments, "tail").style().clickEventUrl());
    }

    @Test
    void linkTextSupportsInlineFormatting() {
        List<Segment> segments = parse("[**b** c](https://example.com)");

        Segment b = find(segments, "b");
        assertEquals(Boolean.TRUE, b.style().bold());
        assertEquals("https://example.com", b.style().clickEventUrl());

        Segment c = find(segments, " c");
        assertNull(c.style().bold());
        assertEquals("https://example.com", c.style().clickEventUrl());
    }

    @Test
    void unmatchedBracketIsLiteralAndDoesNotSwallowText() {
        List<Segment> segments = parse("[note] text and more");

        assertEquals("[note] text and more", plain(segments));
        assertNoLinks(segments);
    }

    @Test
    void linkMissingClosingParenIsLiteral() {
        List<Segment> segments = parse("[x](https://example.com");

        assertEquals("[x](https://example.com", plain(segments));
        assertNoLinks(segments);
    }

    @Test
    void linkCannotSpanLines() {
        List<Segment> segments = parse("[x\n](https://example.com)");

        assertNoLinks(segments);
    }

    @Test
    void nonHttpUrlsAreRejected() {
        List<Segment> segments = parse("[x](javascript:alert1)");

        assertEquals("[x](javascript:alert1)", plain(segments));
        assertNoLinks(segments);
    }

    @Test
    void escapedParenthesisInUrl() {
        List<Segment> segments = parse("[x](https://example.com/a\\))");

        assertEquals("https://example.com/a)", find(segments, "x").style().clickEventUrl());
    }

    @Test
    void balancedParenthesesStayInTheUrl() {
        List<Segment> segments = parse("[x](https://en.wikipedia.org/wiki/Foo_(bar)) tail");

        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", find(segments, "x").style().clickEventUrl());
        assertEquals("x tail", plain(segments));
    }

    @Test
    void unbalancedParenthesesInTheUrlAreNotALink() {
        List<Segment> segments = parse("[x](https://example.com/(a)");

        assertEquals("[x](https://example.com/(a)", plain(segments));
        assertNoLinks(segments);
    }

    // ---- Block elements --------------------------------------------------------------------------------------

    @Test
    void headingIsBoldColoredAndEndsAtLineEnd() {
        List<Segment> segments = parse("# Title\nbody");

        Segment title = find(segments, "Title");
        assertEquals(Boolean.TRUE, title.style().bold());
        assertEquals(Options.DEFAULT.headingColor(), title.style().color());

        Segment body = find(segments, "body");
        assertNull(body.style().bold());
        assertNull(body.style().color());
    }

    @Test
    void headingIsNotItalicByDefault() {
        Segment title = find(parse("# Title"), "Title");

        assertNull(title.style().italic());
    }

    @Test
    void headingInsideBlockQuoteIsNotItalic() {
        List<Segment> segments = parse("> # Title\n> quoted text");

        // The quote prefix in front of a heading is not italic either
        Segment prefix = segments.get(0);
        assertEquals(Options.DEFAULT.quoteStyle(), prefix.text());
        assertNull(prefix.style().italic());
        assertEquals(Options.DEFAULT.quoteColor(), prefix.style().color());

        Segment title = find(segments, "Title");
        assertNull(title.style().italic());
        assertEquals(Boolean.TRUE, title.style().bold());
        assertEquals(Options.DEFAULT.headingColor(), title.style().color());

        // The quote itself keeps its italics
        assertEquals(Boolean.TRUE, find(segments, "quoted text").style().italic());
    }

    @Test
    void headerInsideBlockQuote() {
        List<Segment> segments = parse("> # Header Inside Quote");

        assertTrue(plain(segments).contains(Options.DEFAULT.quoteStyle()), "Should contain the configured quote prefix");
        assertTrue(plain(segments).contains("Header Inside Quote"), "Should contain the header text");

        for (Segment n : segments) {
            assertNull(n.style().italic(), "no italics anywhere on a heading line: '" + n.text() + "'");
        }
        Segment header = find(segments, "Header Inside Quote");
        assertEquals(Boolean.TRUE, header.style().bold());
        assertEquals(Options.DEFAULT.headingColor(), header.style().color());
        assertEquals(Options.DEFAULT.quoteColor(), segments.get(0).style().color());
    }

    @Test
    void quotePrefixIsItalicOnNonHeadingLines() {
        List<Segment> segments = parse("> text");

        assertEquals(Boolean.TRUE, segments.get(0).style().italic());
    }

    // ---- Inline markup inside headings -----------------------------------------------------------------------

    @Test
    void headingSupportsItalicAndStaysBold() {
        Segment world = find(parse("# Hello *world*"), "world");

        assertEquals(Boolean.TRUE, world.style().italic());
        assertEquals(Boolean.TRUE, world.style().bold());
    }

    @Test
    void headingSupportsLinks() {
        List<Segment> segments = parse("# See [docs](https://example.com) now");

        Segment docs = find(segments, "docs");
        assertEquals("https://example.com", docs.style().clickEventUrl());
        assertEquals(Boolean.TRUE, docs.style().bold());
        assertEquals(Options.DEFAULT.linkColor(), docs.style().color());

        assertNull(find(segments, "now").style().clickEventUrl());
    }

    @Test
    void headingSupportsColorTags() {
        List<Segment> segments = parse("# <color:red>Hot</color> stuff");

        Segment hot = find(segments, "Hot");
        assertEquals(color("red"), hot.style().color());
        assertEquals(Boolean.TRUE, hot.style().bold());

        // Outside the tag the heading color applies again
        assertEquals(Options.DEFAULT.headingColor(), find(segments, "stuff").style().color());
    }

    @Test
    void headingHonoursEscapesAndKeepsLiteralHashes() {
        assertEquals("2 * 3", plain(parse("# 2 \\* 3")));
        assertEquals("a # b", plain(parse("# a # b")));
    }

    @Test
    void unclosedMarkerInHeadingDoesNotLeakIntoNextLine() {
        Segment body = find(parse("# a *b\nnext"), "next");

        assertNull(body.style().italic());
        assertNull(body.style().bold());
        assertNull(body.style().color());
    }

    @Test
    void emptyHeadingEndsAtLineEnd() {
        Segment body = find(parse("# \nbody"), "body");

        assertNull(body.style().bold());
    }

    @Test
    void backslashAtEndOfHeadingDoesNotContinueTheHeading() {
        Segment body = find(parse("# Title\\\nbody"), "body");

        assertNull(body.style().bold());
        assertNull(body.style().color());
    }

    @Test
    void blockQuoteAfterHeadingStartsCleanly() {
        List<Segment> segments = parse("# Title\n> quoted");

        assertNull(find(segments, "Title").style().italic());
        assertEquals(Boolean.TRUE, find(segments, "quoted").style().italic());
        assertNull(find(segments, "quoted").style().bold());
    }

    // ---- Behaviour stated in the MarkdownParser documentation ------------------------------------------------

    @Test
    void headingMarkerRules() {
        assertEquals(Boolean.TRUE, find(parse("###### six"), "six").style().bold());
        // Seven hashes is not a heading
        Segment seven = find(parse("####### seven"), "seven");
        assertNull(seven.style().bold());
        assertEquals("####### seven", plain(parse("####### seven")));
    }

    @Test
    void blockMarkersMustStartTheLine() {
        List<Segment> segments = parse("  # indented");

        assertEquals("  # indented", plain(segments));
        assertNull(segments.get(0).style().bold());
    }

    @Test
    void backslashMakesABlockMarkerLiteral() {
        List<Segment> segments = parse("\\# not a heading");

        assertEquals("# not a heading", plain(segments));
        assertNull(segments.get(0).style().bold());
    }

    @Test
    void starAtLineStartIsABulletButAroundTextIsItalic() {
        assertEquals(Options.DEFAULT.bulletStyle() + "item", plain(parse("* item")));
        assertEquals(Boolean.TRUE, find(parse("*item*"), "item").style().italic());
    }

    @Test
    void unclosedMarkerLastsUntilTheEndOfTheParagraph() {
        List<Segment> segments = parse("*a\nb\n\nc");

        assertEquals(Boolean.TRUE, find(segments, "b").style().italic());   // wrapped line, same paragraph
        assertNull(find(segments, "c").style().italic());                   // after the blank line
    }

    @Test
    void explicitColorBeatsHeadingAndQuoteColorsButNotLinkColor() {
        assertEquals(color("red"), find(parse("# <color:red>x</color>"), "x").style().color());
        assertEquals(color("red"), find(parse("> <color:red>x</color>"), "x").style().color());
        assertEquals(Options.DEFAULT.linkColor(),
                find(parse("<color:red>[x](https://example.com)</color>"), "x").style().color());
    }

    @Test
    void colorOverridesLinkOptionLetsAColorTagSetTheLinkColor() {
        Options options = Options.builder().colorOverridesLink(true).build();

        assertEquals(color("red"), find(parse("<color:red>[x](https://example.com)</color>", options), "x").style().color());
        assertEquals(color("red"), find(parse("[<color:red>x</color>](https://example.com)", options), "x").style().color());
        assertEquals(Options.DEFAULT.linkColor(), find(parse("[x](https://example.com)", options), "x").style().color(),
                "without a color tag the link color still applies");
        assertEquals(Boolean.TRUE, find(parse("<color:red>[x](https://example.com)</color>", options), "x").style().underline(),
                "still underlined as a link");
    }

    // ---- Link titles -----------------------------------------------------------------------------------------

    @Test
    void linkTitleInDoubleOrSingleQuotes() {
        Segment link = find(parse("[site](https://example.com \"Go there\") tail"), "site");
        assertEquals("https://example.com", link.style().clickEventUrl());
        assertEquals("Go there", link.style().linkTitle());

        assertEquals("Go there", find(parse("[site](https://example.com 'Go there')"), "site").style().linkTitle());
    }

    @Test
    void linkWithoutATitleHasNone() {
        assertNull(find(parse("[site](https://example.com)"), "site").style().linkTitle());
        assertNull(find(parse("[site](https://example.com \"\")"), "site").style().linkTitle(), "blank title");
        assertNull(find(parse("[a](https://a.com \"A\") [b](https://b.com)"), "b").style().linkTitle(),
                "a title doesn't carry over to the next link");
    }

    @Test
    void linkTitleCanHoldEscapedQuotesMarkupAndOtherQuotes() {
        assertEquals("Say \"hi\"", find(parse("[x](https://example.com \"Say \\\"hi\\\"\")"), "x").style().linkTitle());
        assertEquals("Don't", find(parse("[x](https://example.com \"Don't\")"), "x").style().linkTitle());
        assertEquals("**not bold** [or a link]", find(parse("[x](https://example.com \"**not bold** [or a link]\")"), "x").style().linkTitle());
        assertEquals("a (b) c", find(parse("[x](https://example.com \"a (b) c\")"), "x").style().linkTitle());
    }

    @Test
    void malformedTitleMakesTheLinkLiteral() {
        for (String markdown : List.of(
                "[x](https://example.com Go)",               // not quoted
                "[x](https://example.com \"Go)",             // unclosed
                "[x](https://example.com \"Go' )",           // mismatched quotes
                "[x](https://example.com \"Go\" more)",      // something after it
                "[x](https://example.com \"a\"b\")")) {      // unescaped quote inside
            List<Segment> segments = parse(markdown);
            assertNoLinks(segments);
            assertEquals(markdown, plain(segments), markdown);
        }
    }

    // ---- Escaping --------------------------------------------------------------------------------------------

    @Test
    void escapedTextIsShownAsWritten() {
        for (String text : List.of("**not bold**", "a_b__c", "[x](https://example.com)", "<color:red>x</color>",
                "# not a heading", "C:\\path", "1 * 2 ~~ 3", "plain")) {
            List<Segment> segments = parse(MarkdownParser.escape(text));
            assertEquals(text, plain(segments), text);
            assertNoLinks(segments);
            for (Segment s : segments) {
                assertNull(s.style().bold(), text);
                assertNull(s.style().color(), text);
            }
        }
        assertNull(MarkdownParser.escape(null));
    }

    @Test
    void escapedUrlStillWorksAsALinkDestination() {
        String url = "https://example.com/a_(b)/c*d?x=1#top";
        Segment link = find(parse("[x](" + MarkdownParser.escape(url) + ")"), "x");

        assertEquals(url, link.style().clickEventUrl());
    }

    @Test
    void unsupportedSyntaxIsShownAsWritten() {
        assertEquals("1. one", plain(parse("1. one")));
        assertEquals("`code`", plain(parse("`code`")));
        assertEquals("---", plain(parse("---")));
    }

    @Test
    void imageSyntaxBecomesALiteralBangFollowedByALink() {
        List<Segment> segments = parse("![alt](https://example.com/a.png)");

        assertEquals("!alt", plain(segments));
        assertEquals("https://example.com/a.png", find(segments, "alt").style().clickEventUrl());
    }

    @Test
    void hashWithoutSpaceIsPlainText() {
        List<Segment> segments = parse("#nospace");

        assertEquals("#nospace", plain(segments));
        assertNull(segments.get(0).style().bold());
    }

    @Test
    void blockQuoteStylingEndsAfterQuote() {
        List<Segment> segments = parse("> quoted\n\nplain");

        Segment quoted = find(segments, "quoted");
        assertEquals(Boolean.TRUE, quoted.style().italic());
        assertEquals(Options.DEFAULT.quoteColor(), quoted.style().color());
        assertTrue(quoted.text().startsWith(Options.DEFAULT.quoteStyle()));

        assertNull(find(segments, "plain").style().italic());
    }

    @Test
    void consecutiveQuoteLinesAreStyledIdentically() {
        // Regression: the first quoted line used to be non-italic while later lines were italic.
        List<Segment> segments = parse("> one\n> two");

        assertEquals(Boolean.TRUE, find(segments, "one").style().italic());
        assertEquals(Boolean.TRUE, find(segments, "two").style().italic());
        assertTrue(find(segments, "two").text().startsWith(Options.DEFAULT.quoteStyle()));
    }

    @Test
    void quoteItalicCanBeDisabled() {
        Options options = Options.builder().quoteItalic(false).build();
        List<Segment> segments = parse("> one\n> two", options);

        assertNull(find(segments, "one").style().italic());
        assertNull(find(segments, "two").style().italic());
        // Everything else about the quote is unchanged
        assertEquals(Options.DEFAULT.quoteColor(), find(segments, "one").style().color());
        assertTrue(find(segments, "one").text().startsWith(Options.DEFAULT.quoteStyle()));
    }

    @Test
    void explicitItalicStillWorksInsideQuoteWhenQuoteItalicIsOff() {
        Options options = Options.builder().quoteItalic(false).build();
        List<Segment> segments = parse("> a *b* c", options);

        assertEquals(Boolean.TRUE, find(segments, "b").style().italic());
        assertNull(find(segments, "c").style().italic());
    }

    @Test
    void inlineStyleInsideQuoteDoesNotLeakOut() {
        List<Segment> segments = parse("> a **b** c\n\nplain");

        assertEquals(Boolean.TRUE, find(segments, "b").style().bold());
        Segment plain = find(segments, "plain");
        assertNull(plain.style().bold());
        assertNull(plain.style().italic());
        assertNull(plain.style().color());
    }

    @Test
    void bulletUsesBulletStyleAndColor() {
        List<Segment> segments = parse("- item");

        Segment bullet = segments.get(0);
        assertEquals(Options.DEFAULT.bulletStyle(), bullet.text());
        assertEquals(Options.DEFAULT.bulletColor(), bullet.style().color());
        assertEquals(Options.DEFAULT.bulletStyle() + "item", plain(segments));
    }

    @Test
    void customBulletIsHonoured() {
        Options options = Options.builder().bulletStyle("+ ").bulletColor("").build();

        assertEquals("+ item", plain(parse("- item", options)));
    }

    @Test
    void softBreakBecomesSpaceAndBlankLineBecomesNewlines() {
        assertEquals("one two", plain(parse("one\ntwo")));
        assertEquals("a\n\nb", plain(parse("a\n\nb")));
    }

    @Test
    void whitespaceOnlyLineIsABlankLine() {
        assertEquals("a\n\nb", plain(parse("a\n  \nb")));
        assertEquals("a\n\nb", plain(parse("a\n\t \nb")));

        Segment after = find(parse("*a\n \nb"), "b");
        assertNull(after.style().italic());
    }

    @Test
    void lineThatOnlyLooksLikeABlockMarkerIsASoftBreak() {
        assertEquals("see #tag", plain(parse("see\n#tag")));
        assertEquals("a >b", plain(parse("a\n>b")));
        assertEquals("a -b", plain(parse("a\n-b")));

        // Inline styles carry on across the soft break
        assertEquals(Boolean.TRUE, find(parse("**a\n#tag**"), "#tag").style().bold());
    }

    // ---- Options applied to output ---------------------------------------------------------------------------

    @Test
    void fontOptionIsAppliedToSegments() {
        List<Segment> segments = parse("x", Options.UNIFORM);

        assertEquals(Options.BuiltinFonts.UNIFORM, find(segments, "x").style().font());
    }

    @Test
    void textColorOptionIsAppliedToPlainText() {
        Options options = Options.builder().textColor("white").build();

        assertEquals(color("white"), find(parse("x", options), "x").style().color());
    }

    @Test
    void customHeadingColorIsUsed() {
        Options options = Options.builder().headingColor("red").build();

        assertEquals(color("red"), find(parse("# Hi", options), "Hi").style().color());
    }

    // ---- Input handling --------------------------------------------------------------------------------------

    @Test
    void emptyInputProducesNoSegments() {
        List<Segment> segments = parse("");

        assertTrue(segments.isEmpty());
    }

    @Test
    void windowsLineEndingsAreNormalized() {
        assertEquals(plain(parse("a\n\nb")), plain(parse("a\r\n\r\nb")));
        assertFalse(plain(parse("a\r\nb")).contains("\r"));
    }

    @Test
    void quotesAndBackslashesSurviveIntact() {
        assertEquals("say \"hi\"", plain(parse("say \"hi\"")));
        assertEquals("a\\b", plain(parse("a\\\\b"))); // markdown "a\\b" is an escaped backslash
    }

    @Test
    void backslashBeforeNonPunctuationIsLiteral() {
        assertEquals("C:\\config\\dsurround", plain(parse("C:\\config\\dsurround")));
        assertEquals("a\\ b", plain(parse("a\\ b")));
        assertEquals("\\", plain(parse("\\")));
    }

    @Test
    void backslashBeforeNewlineIsLiteralAndNotALineBreak() {
        assertEquals(Options.DEFAULT.quoteStyle() + "a\\ b", plain(parse("> a\\\nb")));
        assertEquals("a\\\n" + Options.DEFAULT.bulletStyle() + "x", plain(parse("a\\\n- x")));
    }

    // ---- Options builder -------------------------------------------------------------------------------------

    @Test
    void fontCanBeSetAndCleared() {
        assertEquals(Options.BuiltinFonts.GALACTIC, Options.builder().font(Options.BuiltinFonts.GALACTIC).build().font());
        assertNull(Options.builder().font(Options.BuiltinFonts.GALACTIC).font(null).build().font());
    }

    @Test
    void defaultsAreSensible() {
        Options d = Options.DEFAULT;

        assertNotNull(d.headingColor());
        assertNotNull(d.linkColor());
        assertNull(d.textColor());
        assertNull(d.font());
        assertFalse(d.bulletStyle().isEmpty());
        assertFalse(d.quoteStyle().isEmpty());
        assertEquals("%s", d.linkHoverTemplate());
    }

    @Test
    void builderColorValuesAreKept() {
        // Regression: the old builder had its blank check inverted and discarded every non-blank value.
        Options o = Options.builder()
                .headingColor("red").linkColor("green").bulletColor("blue")
                .textColor("white").quoteColor("gray")
                .build();

        assertEquals(color("red"), o.headingColor());
        assertEquals(color("green"), o.linkColor());
        assertEquals(color("blue"), o.bulletColor());
        assertEquals(color("white"), o.textColor());
        assertEquals(color("gray"), o.quoteColor());
    }

    @Test
    void builderAcceptsTextColorObjectsAndNullClearsThem() {
        Options o = Options.builder().headingColor(color("red")).linkColor((TextColor) null).build();

        assertEquals(color("red"), o.headingColor());
        assertNull(o.linkColor());
    }

    @Test
    void unrecognizedColorStringClearsToNull() {
        Options o = Options.builder().headingColor("notacolor").linkColor("#fff").bulletColor("+ff").build();

        assertNull(o.headingColor());
        assertNull(o.linkColor());
        assertNull(o.bulletColor());
    }

    @Test
    void colorStringsAreCaseInsensitiveAndTrimmed() {
        Options o = Options.builder().headingColor(" RED ").linkColor(" #FF0000 ").build();

        assertEquals(color("red"), o.headingColor());
        assertEquals(color("#ff0000"), o.linkColor());
    }

    @Test
    void blankColorClearsToNull() {
        Options o = Options.builder().headingColor("  ").linkColor("").build();

        assertNull(o.headingColor());
        assertNull(o.linkColor());
    }

    @Test
    void stringOptionsKeepCustomValuesAndFallBackWhenNull() {
        Options custom = Options.builder().bulletStyle("- ").quoteStyle("> ").build();
        assertEquals("- ", custom.bulletStyle());
        assertEquals("> ", custom.quoteStyle());

        Options fallback = Options.builder().bulletStyle(null).quoteStyle(null).build();
        assertEquals(Options.DEFAULT.bulletStyle(), fallback.bulletStyle());
        assertEquals(Options.DEFAULT.quoteStyle(), fallback.quoteStyle());
    }

    @Test
    void quoteItalicDefaultsToTrueAndCanBeChanged() {
        assertTrue(Options.DEFAULT.quoteItalic());
        assertTrue(Options.builder().build().quoteItalic());
        assertFalse(Options.builder().quoteItalic(false).build().quoteItalic());
    }

    @Test
    void emptyBulletAndQuotePrefixAreAllowed() {
        Options o = Options.builder().bulletStyle("").quoteStyle("").build();

        assertEquals("", o.bulletStyle());
        assertEquals("", o.quoteStyle());
    }

    @Test
    void hoverTemplateAndTranslationKeyRules() {
        assertEquals("%s", Options.builder().linkHoverTemplate("  ").build().linkHoverTemplate());
        assertEquals("Go: %s", Options.builder().linkHoverTemplate("Go: %s").build().linkHoverTemplate());

        assertEquals(Options.DEFAULT.linkHoverTranslationKey(),
                Options.builder().linkHoverTranslationKey(null).build().linkHoverTranslationKey());
        assertEquals("", Options.builder().linkHoverTranslationKey("").build().linkHoverTranslationKey());
    }

    @Test
    void builtOptionsAreNotAffectedByLaterBuilderChanges() {
        Options.Builder builder = Options.builder().bulletStyle("a ");
        Options first = builder.build();
        builder.bulletStyle("b ");

        assertEquals("a ", first.bulletStyle());
        assertEquals("b ", builder.build().bulletStyle());
    }
}