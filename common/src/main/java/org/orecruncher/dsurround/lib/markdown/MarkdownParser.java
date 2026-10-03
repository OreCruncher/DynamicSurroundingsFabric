package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts a small, Minecraft-oriented subset of Markdown into a {@link Component}.
 *
 * <h2>Supported syntax</h2>
 * <table>
 *   <caption>Markup and what it produces</caption>
 *   <tr><th>Markup</th><th>Result</th></tr>
 *   <tr><td>{@code # Heading} (one to six {@code #}, then a space)</td>
 *       <td>Bold text in the heading color. The level does not change the appearance. Inline markup, links and
 *           color tags work inside a heading. Closing {@code #}s are not stripped.</td></tr>
 *   <tr><td>{@code **text**}</td><td>Bold</td></tr>
 *   <tr><td>{@code *text*}</td><td>Italic</td></tr>
 *   <tr><td>{@code __text__}</td>
 *       <td>Underline. <b>This differs from CommonMark</b>, where {@code __} means bold.</td></tr>
 *   <tr><td>{@code ~~text~~}</td><td>Strikethrough</td></tr>
 *   <tr><td>{@code [text](https://example.com)}</td>
 *       <td>A link in the link color, underlined, that opens the URL when clicked and shows hover text. Only
 *           {@code http} and {@code https} URLs are links, and the whole {@code [text](url)} must be on one line.
 *           Parentheses in the URL must balance, or be escaped as {@code \)}. The link text may contain inline
 *           markup.</td></tr>
 *   <tr><td>{@code [text](https://example.com "Hover text")}</td>
 *       <td>A link with a title, in double or single quotes, which is shown as its hover text instead of the
 *           URL. The title is plain text. A quote of the same kind inside it is escaped as {@code \"}, and a
 *           {@code )} must balance or be escaped, as in the URL. Anything else after the URL makes the whole link
 *           literal text.</td></tr>
 *   <tr><td>{@code <color:red>text</color>}, {@code <color:#FF8800>text</color>}</td>
 *       <td>Colored text. Names are the 16 Minecraft colors ({@code gold}, {@code dark_red}, ...). The tags are not
 *           case sensitive. Color tags may be nested. An explicit color overrides the heading and quote colors, but
 *           not the link color unless {@link Options#colorOverridesLink()} is on.</td></tr>
 *   <tr><td>{@code - item} or {@code * item}</td>
 *       <td>A bullet, using {@link Options#bulletStyle()}. One level only.</td></tr>
 *   <tr><td>{@code > text}</td>
 *       <td>A block quote, using {@link Options#quoteStyle()} as a prefix on every quoted line. Italic unless
 *           {@link Options#quoteItalic()} is off. Headings inside a quote are never italic.</td></tr>
 *   <tr><td>{@code \*}</td><td>A backslash makes the next character literal if it is ASCII punctuation, as in
 *       CommonMark. Before anything else the backslash is itself literal, so {@code C:\path} is shown as
 *       written.</td></tr>
 * </table>
 *
 * <h2>Lines and paragraphs</h2>
 * A block marker ({@code #}, {@code >}, {@code -}, {@code *}) is only recognized at the very start of a line, with
 * no indentation. A single newline inside a paragraph is a "soft break" and becomes one space. A blank line (one
 * that is empty or holds only spaces and tabs), or a line beginning with a block marker, starts a new block and
 * keeps its newline.
 * <p>
 * Inline markers do not carry across blocks: an unclosed {@code *} or {@code **} lasts until the end of its
 * paragraph (the next blank line or block marker) and then stops. {@code <color:>} tags are the exception, since
 * they are closed explicitly by {@code </color>}.
 * <p>
 * A {@code *}, {@code **}, {@code __} or {@code ~~} with whitespace on both sides, as in {@code 2 * 3}, is literal
 * text and does not start emphasis. If that style is already open, such a marker closes it, so
 * {@code **bold ** text} still works. The start and end of the text count as whitespace.
 *
 * <h2>Malformed and unsupported input</h2>
 * The parser never rejects input. Anything it cannot interpret is shown as literal text, including an unclosed
 * {@code [} or {@code <color:}, a color tag naming a color Minecraft doesn't have (along with its matching
 * {@code </color>}), and a link whose URL isn't {@code http} or {@code https}. A closing {@code </color>} with no
 * opening tag is silently dropped.
 * <p>
 * Not supported, and shown as written: numbered and nested lists, inline code and code blocks, tables,
 * horizontal rules, HTML other than {@code <color>}, and hard line breaks. Images are not supported either:
 * {@code ![alt](url)} shows a literal {@code !} followed by {@code alt} as an ordinary link.
 *
 * <h2>Threading</h2>
 * All methods are stateless and safe to call from any thread. {@link Options} instances are immutable.
 */
public final class MarkdownParser {

    private MarkdownParser() {
    }

    /**
     * Converts the mark-down document into a Component representation for rendering, using {@link Options#DEFAULT}.
     *
     * @param markdown the document to convert
     * @return the rendered component, or empty if {@code markdown} is null
     */
    public static Optional<Component> markdownToComponent(String markdown) {
        return markdownToComponent(markdown, Options.DEFAULT);
    }

    /**
     * Converts the mark-down document into a Component using custom options. Malformed markup is rendered as
     * literal text rather than rejected.
     *
     * @param markdown      the document to convert
     * @param parserOptions styling options for headings, links, quotes and so on
     * @return the rendered component, or empty if {@code markdown} is null
     */
    public static Optional<Component> markdownToComponent(String markdown, Options parserOptions) {
        Objects.requireNonNull(parserOptions, "parserOptions");
        if (markdown == null) {
            return Optional.empty();
        }
        List<Segment> segments = parseToSegments(markdown, parserOptions);
        return Optional.of(ComponentExporter.export(segments, parserOptions));
    }

    /**
     * Escapes {@code text} so it shows as written when inserted into a document: each ASCII punctuation character
     * gets a backslash, so nothing in it is taken as markup. Safe in a link's URL too, as escaped characters keep
     * their meaning there. Not for a link's title, which is plain text.
     *
     * @return the escaped text, or null if {@code text} is null
     */
    public static String escape(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Lexer.isEscapable(c)) {
                result.append('\\');
            }
            result.append(c);
        }
        return result.toString();
    }

    static List<Segment> parseToSegments(String markdown) {
        return parseToSegments(markdown, Options.DEFAULT);
    }

    /**
     * Parses a Markdown string into a flat, optimized list of styled text segments. Package-private so tests can
     * inspect the result without needing Minecraft's Component classes.
     */
    static List<Segment> parseToSegments(String markdown, Options options) {

        // On Windows newlines are a bit different so we need to clean them up
        markdown = markdown.replace("\r\n", "\n").replace('\r', '\n');

        List<Token> tokens = new Lexer(markdown).tokenize();
        List<Segment> segments = new SegmentBuilder(tokens, options).build();
        return Optimizer.optimize(segments);
    }
}