package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the conversion from the parsed node tree to real Minecraft Components (1.21.1 API).
 * <p>
 * Minecraft's Style class is deliberately not imported: this package has its own Style type, so results of
 * {@code getStyle()} are held in {@code var}s instead.
 * Expected location: src/test/java/org/orecruncher/dsurround/lib/markdown/
 */
class ComponentExporterTests {

    // Need this to bootstrap the Minecraft environment so tests run
    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- Helpers ---------------------------------------------------------------------------------------------

    private static Component build(String markdown) {
        return build(markdown, Options.DEFAULT);
    }

    private static Component build(String markdown, Options options) {
        return MarkdownParser.markdownToComponent(markdown, options).orElseThrow();
    }

    /**
     * The single child of the root component. Fails if there isn't exactly one.
     */
    private static Component onlyChild(Component root) {
        assertEquals(1, root.getSiblings().size());
        return root.getSiblings().get(0);
    }

    // ---- Basic structure -------------------------------------------------------------------------------------

    @Test
    void nullMarkdownGivesEmptyOptional() {
        assertTrue(MarkdownParser.markdownToComponent(null).isEmpty());
    }

    @Test
    void emptyMarkdownGivesEmptyComponent() {
        Component root = build("");

        assertEquals("", root.getString());
        assertTrue(root.getSiblings().isEmpty());
    }

    @Test
    void textIsPreservedAcrossStyledSegments() {
        assertEquals("hello world", build("hello **world**").getString());
    }

    @Test
    void oneChildPerSegmentInOrder() {
        List<Segment> segments = List.of(
                new Segment("a", Style.EMPTY),
                new Segment("b", Style.EMPTY.withItalic(Boolean.TRUE)));

        Component root = ComponentExporter.export(segments, Options.DEFAULT);

        assertEquals(2, root.getSiblings().size());
        assertEquals("a", root.getSiblings().get(0).getString());
        assertEquals("b", root.getSiblings().get(1).getString());
        assertTrue(root.getSiblings().get(1).getStyle().isItalic());
    }

    // ---- Text styles -----------------------------------------------------------------------------------------

    @Test
    void boldItalicUnderlineAndStrikethroughAreApplied() {
        assertTrue(onlyChild(build("**x**")).getStyle().isBold());
        assertTrue(onlyChild(build("*x*")).getStyle().isItalic());
        assertTrue(onlyChild(build("__x__")).getStyle().isUnderlined());
        assertTrue(onlyChild(build("~~x~~")).getStyle().isStrikethrough());
    }

    @Test
    void unstyledTextHasNoStylingSet() {
        var style = onlyChild(build("plain")).getStyle();

        assertFalse(style.isBold());
        assertFalse(style.isItalic());
        assertFalse(style.isUnderlined());
        assertFalse(style.isStrikethrough());
        assertNull(style.getColor());
        assertNull(style.getClickEvent());
        assertNull(style.getHoverEvent());
    }

    @Test
    void stylingStopsWhereTheMarkerEnds() {
        Component root = build("**a** b");

        assertEquals(2, root.getSiblings().size());
        assertTrue(root.getSiblings().get(0).getStyle().isBold());
        assertFalse(root.getSiblings().get(1).getStyle().isBold());
    }

    // ---- Colors ----------------------------------------------------------------------------------------------

    @Test
    void colorInsideBoldIsBoldAndColoredOnTheSameComponent() {
        Component c = onlyChild(build("**<color:#FF0000>Red Bold Text</color>**"));

        assertEquals("Red Bold Text", c.getString());
        assertTrue(c.getStyle().isBold());
        assertEquals(0xFF0000, c.getStyle().getColor().getValue());
    }

    @Test
    void namedColorIsApplied() {
        Component c = onlyChild(build("<color:red>x</color>"));

        assertEquals("red", c.getStyle().getColor().serialize());
    }

    @Test
    void hexColorIsAppliedRegardlessOfCase() {
        assertEquals(0xFF00AA, onlyChild(build("<color:#ff00aa>x</color>")).getStyle().getColor().getValue());
        assertEquals(0xFF00AA, onlyChild(build("<color:#FF00AA>x</color>")).getStyle().getColor().getValue());
    }

    @Test
    void segmentColorIsAppliedToTheComponent() {
        TextColor gold = TextColor.parseColor("gold").result().orElseThrow();
        List<Segment> segments = List.of(new Segment("x", Style.EMPTY.withColor(gold)));

        Component c = onlyChild(ComponentExporter.export(segments, Options.DEFAULT));

        assertEquals(gold, c.getStyle().getColor());
    }

    @Test
    void headerInsideBlockQuote() {
        Component root = build("> # Header Inside Quote");

        assertTrue(root.getString().contains(Options.DEFAULT.quoteStyle()));
        assertTrue(root.getString().contains("Header Inside Quote"));
        for (Component c : root.getSiblings()) {
            assertFalse(c.getStyle().isItalic(), "unexpected italics on '" + c.getString() + "'");
        }

        Component header = root.getSiblings().get(root.getSiblings().size() - 1);
        assertEquals("Header Inside Quote", header.getString());
        assertTrue(header.getStyle().isBold());
        assertEquals(Options.DEFAULT.headingColor().getValue(), header.getStyle().getColor().getValue());
    }

    // ---- Fonts -----------------------------------------------------------------------------------------------

    @Test
    void fontOptionIsApplied() {
        Component c = onlyChild(build("x", Options.UNIFORM));

        assertEquals(Options.BuiltinFonts.UNIFORM, c.getStyle().getFont());
    }

    // ---- Links -----------------------------------------------------------------------------------------------

    @Test
    void linkOpensTheUrl() {
        Component link = build("[site](https://example.com)").getSiblings().get(0);

        assertEquals("site", link.getString());
        assertTrue(link.getStyle().isUnderlined());

        ClickEvent click = link.getStyle().getClickEvent();
        assertNotNull(click);
        assertEquals(ClickEvent.Action.OPEN_URL, click.getAction());
        assertEquals("https://example.com", click.getValue());
    }

    @Test
    void linkHasHoverText() {
        Component link = build("[site](https://example.com)").getSiblings().get(0);

        HoverEvent hover = link.getStyle().getHoverEvent();
        assertNotNull(hover);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hover.getAction());
        assertNotNull(hover.getValue(HoverEvent.Action.SHOW_TEXT));
    }

    @Test
    void hoverTextUsesTheTemplateWhenTranslationIsDisabled() {
        Options options = Options.builder().linkHoverTranslationKey("").linkHoverTemplate("Open %s").build();
        Component link = build("[site](https://example.com)", options).getSiblings().get(0);

        Component hoverText = link.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        assertEquals("Open https://example.com", hoverText.getString());
    }

    @Test
    void hoverTemplateWithStrayPercentDoesNotThrow() {
        Options options = Options.builder().linkHoverTranslationKey("").linkHoverTemplate("100% of %s").build();
        Component link = build("[site](https://example.com)", options).getSiblings().get(0);

        Component hoverText = link.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        assertEquals("100% of https://example.com", hoverText.getString());
    }

    @Test
    void nullOptionsAreRejected() {
        assertThrows(NullPointerException.class, () -> MarkdownParser.markdownToComponent("x", null));
    }

    @Test
    void textAfterALinkHasNoLinkBehaviour() {
        Component root = build("[site](https://example.com) tail");

        assertEquals(2, root.getSiblings().size());
        assertNull(root.getSiblings().get(1).getStyle().getClickEvent());
        assertNull(root.getSiblings().get(1).getStyle().getHoverEvent());
    }

    @Test
    void rejectedLinkProducesNoClickEvent() {
        Component root = build("[x](javascript:alert1)");

        assertEquals("[x](javascript:alert1)", root.getString());
        for (Component c : root.getSiblings()) {
            assertNull(c.getStyle().getClickEvent());
        }
    }
}