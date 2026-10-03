package org.orecruncher.dsurround.lib.markdown;

import net.minecraft.network.chat.TextColor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Walks the token stream produced by {@link Lexer} and builds a flat list of styled {@link Segment}s.
 * <p>
 * The parser tracks three independent pieces of state and derives the {@link Style} for each segment from them:
 * <ul>
 *   <li>inline flags (bold, italic, underline, strikethrough) - toggled by markers, reset at block boundaries</li>
 *   <li>block context (heading, block quote) - set by line-start markers, cleared at the end of the line</li>
 *   <li>explicit colors from {@code <color:x>...</color>} - a real stack, pushed/popped only by those tags. An
 *       unrecognized color is shown literally, and so is the {@code </color>} that pairs with it.</li>
 * </ul>
 * Links are validated up front; anything that doesn't form a complete, safe link is emitted as literal text. A link
 * may have a title after its URL, {@code [text](url "title")} or {@code 'title'}, which becomes its hover text.
 */
class SegmentBuilder {

    private final List<Token> tokens;
    private final Options options;
    private final List<Segment> segments = new ArrayList<>();
    private final Deque<ColorFrame> colors = new ArrayDeque<>();
    private int index = 0;
    // Inline flags
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikethrough;
    // Block context
    private boolean heading;
    private boolean quote;
    // Active link. linkUrl is null when not inside link text. linkTitle is null if the link has no title. linkEnd is
    // the token index of the closing ')'.
    private String linkUrl;
    private String linkTitle;
    private int linkEnd;

    public SegmentBuilder(List<Token> tokens, Options options) {
        this.tokens = tokens;
        this.options = options;
    }

    /**
     * The start and end of the input count as whitespace.
     */
    private static boolean isSurroundedByWhitespace(Token token) {
        CharSequence source = token.source();
        boolean before = token.start() == 0 || Character.isWhitespace(source.charAt(token.start() - 1));
        boolean after = token.end() >= source.length() || Character.isWhitespace(source.charAt(token.end()));
        return before && after;
    }

    private static boolean isSafeUrl(String url) {
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

    public List<Segment> build() {
        while (this.index < this.tokens.size()) {
            Token token = this.tokens.get(this.index++);

            switch (token.type()) {
                case TEXT, ESCAPED -> this.emit(token.source(), token.start(), token.end());
                // Soft breaks are wrapped lines inside a paragraph and act as a single space
                case SOFT_BREAK -> this.emit(" ");
                case NEWLINE -> {
                    this.emit("\n");
                    this.endBlock();
                }
                case HEADER_MARKER -> this.heading = true;
                case BULLET_MARKER -> this.emitBullet();
                case BLOCKQUOTE_MARKER -> {
                    this.quote = true;
                    this.emitQuotePrefix();
                }
                case BOLD_MARKER -> this.bold = this.toggle(token, this.bold);
                case ITALIC_MARKER -> this.italic = this.toggle(token, this.italic);
                case UNDERLINE_MARKER -> this.underline = this.toggle(token, this.underline);
                case STRIKE_MARKER -> this.strikethrough = this.toggle(token, this.strikethrough);
                case COLOR_START -> this.startColor(token);
                case COLOR_END -> this.endColor(token);
                case LINK_START -> this.startLink(token);
                case LINK_MID -> this.endLinkText(token);
                // A ')' that isn't closing a link is just text. Valid links skip past their own ')'.
                default -> this.emit(token.source(), token.start(), token.end());
            }
        }
        return this.segments;
    }

    // ---- Style derivation ------------------------------------------------------------------------------------

    private Style currentStyle() {
        // Quotes are italic when the option is on, but a heading inside a quote never is. Explicit italic markup
        // still applies.
        boolean quoteItalic = this.quote && this.options.quoteItalic() && !this.heading;

        // Flags are null when "off" so they merge cleanly in the Optimizer and stay unset in the output.
        return new Style(
                this.currentColor(),
                this.options.font(),
                (this.bold || this.heading) ? Boolean.TRUE : null,
                (this.italic || quoteItalic) ? Boolean.TRUE : null,
                (this.underline || this.linkUrl != null) ? Boolean.TRUE : null,
                this.strikethrough ? Boolean.TRUE : null,
                this.linkUrl,
                this.linkTitle);
    }

    /**
     * The color for text emitted right now, or null for the renderer default. Precedence, lowest to highest:
     * text < quote < heading < explicit {@code <color>} < link, or with {@link Options#colorOverridesLink()},
     * text < quote < heading < link < explicit {@code <color>}.
     */
    private TextColor currentColor() {
        TextColor color = this.options.textColor();
        if (this.quote && this.options.quoteColor() != null) {
            color = this.options.quoteColor();
        }
        if (this.heading && this.options.headingColor() != null) {
            color = this.options.headingColor();
        }
        TextColor explicit = this.colors.isEmpty() ? null : this.colors.peek().color();
        if (this.linkUrl != null && this.options.linkColor() != null
                && (explicit == null || !this.options.colorOverridesLink())) {
            return this.options.linkColor();
        }
        return explicit != null ? explicit : color;
    }

    // ---- Emitting --------------------------------------------------------------------------------------------

    /**
     * End of a line/paragraph: block context ends and unclosed inline markers don't leak into what follows.
     */
    private void endBlock() {
        this.heading = false;
        this.quote = false;
        this.bold = false;
        this.italic = false;
        this.underline = false;
        this.strikethrough = false;
    }

    private void emit(CharSequence text) {
        this.emit(text, 0, text.length());
    }

    private void emit(CharSequence source, int start, int end) {
        this.emit(this.currentStyle(), source, start, end);
    }

    private void emit(Style style, CharSequence source, int start, int end) {
        if (start >= end) {
            return;
        }
        this.segments.add(new Segment(source.subSequence(start, end).toString(), style));
    }

    /**
     * Emits the quote prefix. The prefix is written before the builder sees a following '#', so it has to look
     * ahead: heading lines are never italic, and that includes the prefix in front of them.
     */
    private void emitQuotePrefix() {
        Style style = this.currentStyle();
        if (this.index < this.tokens.size() && this.tokens.get(this.index).type() == TokenType.HEADER_MARKER) {
            style = style.withItalic(this.italic ? Boolean.TRUE : null);
        }
        String prefix = this.options.quoteStyle();
        this.emit(style, prefix, 0, prefix.length());
    }

    private void emitBullet() {
        Style style = this.currentStyle();
        if (this.options.bulletColor() != null) {
            style = style.withColor(this.options.bulletColor());
        }
        String bullet = this.options.bulletStyle();
        this.emit(style, bullet, 0, bullet.length());
    }

    // ---- Inline style markers --------------------------------------------------------------------------------

    /**
     * Flips an inline style flag for a marker token and returns the new value.
     * <p>
     * A marker with whitespace on both sides, like the {@code *} in "2 * 3", can't begin emphasis, so it is shown as
     * text and the flag stays off. If the style is already on, such a marker still closes it, so sloppy input like
     * "**bold ** text" keeps working.
     */
    private boolean toggle(Token marker, boolean current) {
        if (!current && isSurroundedByWhitespace(marker)) {
            this.emit(marker.source(), marker.start(), marker.end());
            return false;
        }
        return !current;
    }

    // ---- Colors ----------------------------------------------------------------------------------------------

    private void startColor(Token token) {
        TextColor color = Colors.parse(token.argument());
        if (color != null) {
            this.colors.push(new ColorFrame(color, false));
        } else {
            // Unknown color - show the tag literally rather than guess what was meant. A frame is still pushed so
            // the matching </color> closes this tag (and is shown literally too) instead of an enclosing one.
            this.emit(token.source(), token.start(), token.end());
            this.colors.push(new ColorFrame(this.colors.isEmpty() ? null : this.colors.peek().color(), true));
        }
    }

    private void endColor(Token token) {
        if (this.colors.isEmpty()) {
            return; // stray closing tag
        }
        if (this.colors.pop().literal()) {
            this.emit(token.source(), token.start(), token.end());
        }
    }

    // ---- Links -----------------------------------------------------------------------------------------------

    private void startLink(Token token) {
        LinkMatch match = this.linkUrl == null ? this.matchLink(this.index) : null;
        if (match == null) {
            this.emit(token.source(), token.start(), token.end()); // literal '['
            return;
        }
        this.linkUrl = match.url();
        this.linkTitle = match.title();
        this.linkEnd = match.endIndex();
    }

    private void endLinkText(Token token) {
        if (this.linkUrl == null) {
            this.emit(token.source(), token.start(), token.end()); // literal "]("
            return;
        }
        // Link text has been emitted with link styling. Skip over the URL tokens and the closing ')'.
        this.index = this.linkEnd + 1;
        this.linkUrl = null;
        this.linkTitle = null;
    }

    /**
     * Looks ahead from just after a '[' for a matching "](url)" on the same line. Returns null if the link
     * is incomplete or the URL isn't acceptable, in which case the '[' should be treated as text.
     * <p>
     * Parentheses in the URL must balance, so {@code (https://en.wikipedia.org/wiki/Foo_(bar))} keeps its last
     * {@code )}. Escaped parentheses don't count. The same goes for a title, so a {@code )} in one must be escaped
     * or balanced.
     */
    private LinkMatch matchLink(int from) {
        int mid = -1;
        int depth = 0;
        for (int i = from; i < this.tokens.size(); i++) {
            Token token = this.tokens.get(i);
            TokenType type = token.type();
            if (type == TokenType.NEWLINE || type == TokenType.SOFT_BREAK) {
                return null;
            }
            if (mid < 0) {
                if (type == TokenType.LINK_START) {
                    return null;
                }
                if (type == TokenType.LINK_MID) {
                    mid = i;
                }
            } else if (type == TokenType.TEXT) {
                depth += count(token.value(), '(');
            } else if (type == TokenType.LINK_END && depth > 0) {
                depth--;
            } else if (type == TokenType.LINK_END) {
                return this.destination(mid + 1, i);
            }
        }
        return null;
    }

    private static int count(CharSequence text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * The link for the tokens between "](" and ")": a URL, then optionally whitespace and a title in double or single
     * quotes. Null if the URL isn't acceptable, or anything else follows it. Escaped characters are taken literally,
     * so a title can hold its own quote as {@code \"}.
     */
    private LinkMatch destination(int from, int endIndex) {
        // The text, and which of its characters were escaped (an escaped quote doesn't end a title)
        StringBuilder text = new StringBuilder();
        BitSet escaped = new BitSet();
        for (int j = from; j < endIndex; j++) {
            Token token = this.tokens.get(j);
            if (token.type() == TokenType.ESCAPED) {
                escaped.set(text.length());
            }
            text.append(token.value());
        }

        int start = skipWhitespace(text, 0);
        int urlEnd = start;
        while (urlEnd < text.length() && !Character.isWhitespace(text.charAt(urlEnd))) {
            urlEnd++;
        }
        String url = text.substring(start, urlEnd);
        if (!isSafeUrl(url)) {
            return null;
        }

        int titleStart = skipWhitespace(text, urlEnd);
        int titleEnd = text.length();
        while (titleEnd > titleStart && Character.isWhitespace(text.charAt(titleEnd - 1))) {
            titleEnd--;
        }
        if (titleStart == titleEnd) {
            return new LinkMatch(url, null, endIndex);
        }

        // A title: from an unescaped quote to the same quote, with nothing after it
        char quote = text.charAt(titleStart);
        if ((quote != '"' && quote != '\'') || escaped.get(titleStart) || titleEnd - titleStart < 2
                || text.charAt(titleEnd - 1) != quote || escaped.get(titleEnd - 1)) {
            return null;
        }
        for (int k = titleStart + 1; k < titleEnd - 1; k++) {
            if (text.charAt(k) == quote && !escaped.get(k)) {
                return null;
            }
        }
        String title = text.substring(titleStart + 1, titleEnd - 1);
        return new LinkMatch(url, title.isBlank() ? null : title, endIndex);
    }

    private static int skipWhitespace(CharSequence text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private record LinkMatch(String url, String title, int endIndex) {
    }

    /**
     * One open {@code <color>} tag. {@code color} is null when the tag doesn't change the color. {@code literal} is
     * true for a tag with an unrecognized color, which was shown as text.
     */
    private record ColorFrame(TextColor color, boolean literal) {
    }
}