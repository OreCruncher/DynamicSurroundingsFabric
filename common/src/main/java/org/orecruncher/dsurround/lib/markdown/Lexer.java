package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits Markdown text into {@link Token}s. This is purely lexical: it recognizes markers and text but keeps no
 * style state and never decides what a marker means. That is left to {@link SegmentBuilder}.
 */
class Lexer {
    private final CharSequence input;
    private final int length;
    private int pos = 0;

    public Lexer(CharSequence input) {
        this.input = input;
        this.length = this.input.length();
    }

    /**
     * As in CommonMark, only ASCII punctuation can be escaped. A backslash before anything else (a letter, a space,
     * a newline) is literal, so text like {@code C:\path} survives unchanged.
     */
    static boolean isEscapable(char c) {
        return (c >= '!' && c <= '/') || (c >= ':' && c <= '@') || (c >= '[' && c <= '`') || (c >= '{' && c <= '~');
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        boolean atLineStart = true;
        boolean inHeader = false; // true from a heading marker until the end of that line

        while (this.pos < this.length) {
            char c = this.input.charAt(this.pos);

            if (c == '\\' && this.pos + 1 < this.length && isEscapable(this.input.charAt(this.pos + 1))) {
                tokens.add(new Token(TokenType.ESCAPED, this.pos + 1, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '\n') {
                int newlineStart = this.pos;
                this.pos++;
                atLineStart = true;
                boolean wasHeader = inHeader;
                inHeader = false;

                if (this.isBlankLine(newlineStart) || this.startsBlock(this.pos) || wasHeader) {
                    tokens.add(new Token(TokenType.NEWLINE, newlineStart, newlineStart + 1, this.input));
                    // Drop the whitespace on a following blank line so it behaves exactly like an empty one
                    int next = this.skipSpaces(this.pos);
                    if (next < this.length && this.input.charAt(next) == '\n') {
                        this.pos = next;
                    }
                } else {
                    tokens.add(new Token(TokenType.SOFT_BREAK, newlineStart, newlineStart + 1, this.input));
                }
            } else if (atLineStart && this.headingLevelAt(this.pos) > 0) {
                int level = this.headingLevelAt(this.pos);
                // The marker includes the space after the hashes
                tokens.add(new Token(TokenType.HEADER_MARKER, this.pos, this.pos + level + 1, this.input, String.valueOf(level)));
                this.pos += level + 1;
                // The rest of the line is scanned normally so inline markup works inside headings. The newline
                // that ends the line is always a block boundary (see the '\n' branch above).
                inHeader = true;
                atLineStart = false;
            } else if (atLineStart && this.isQuoteAt(this.pos)) {
                tokens.add(new Token(TokenType.BLOCKQUOTE_MARKER, this.pos, this.pos + 1, this.input));
                this.pos++;
                if (this.pos < this.length && this.input.charAt(this.pos) == ' ') {
                    this.pos++;
                }
                // atLineStart stays true so a heading or bullet can follow the quote marker
            } else if (atLineStart && this.isBulletAt(this.pos)) {
                tokens.add(new Token(TokenType.BULLET_MARKER, this.pos, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '*' && this.peekMatch("**")) {
                tokens.add(new Token(TokenType.BOLD_MARKER, this.pos, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '_' && this.peekMatch("__")) {
                tokens.add(new Token(TokenType.UNDERLINE_MARKER, this.pos, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '*') {
                tokens.add(new Token(TokenType.ITALIC_MARKER, this.pos, this.pos + 1, this.input));
                this.pos++;
                atLineStart = false;
            } else if (c == '~' && this.peekMatch("~~")) {
                tokens.add(new Token(TokenType.STRIKE_MARKER, this.pos, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '<' && this.matchIgnoreCase("<color:")) {
                int endIdx = this.indexOf('>', this.pos);
                if (endIdx != -1) {
                    String color = this.input.subSequence(this.pos + 7, endIdx).toString().trim();
                    tokens.add(new Token(TokenType.COLOR_START, this.pos, endIdx + 1, this.input, color));
                    this.pos = endIdx + 1;
                } else {
                    // No closing '>' on this line: treat the '<' as literal text
                    tokens.add(new Token(TokenType.TEXT, this.pos, this.pos + 1, this.input));
                    this.pos++;
                }
                atLineStart = false;
            } else if (c == '<' && this.matchIgnoreCase("</color>")) {
                tokens.add(new Token(TokenType.COLOR_END, this.pos, this.pos + 8, this.input));
                this.pos += 8;
                atLineStart = false;
            } else if (c == '[') {
                tokens.add(new Token(TokenType.LINK_START, this.pos, this.pos + 1, this.input));
                this.pos++;
                atLineStart = false;
            } else if (c == ']' && this.pos + 1 < this.length && this.input.charAt(this.pos + 1) == '(') {
                tokens.add(new Token(TokenType.LINK_MID, this.pos, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.LINK_END, this.pos, this.pos + 1, this.input));
                this.pos++;
                atLineStart = false;
            } else {
                int start = this.pos;
                while (this.pos < this.length && this.isNotSpecial(this.input.charAt(this.pos))) {
                    this.pos++;
                }
                if (start == this.pos) {
                    this.pos++;
                }
                tokens.add(new Token(TokenType.TEXT, start, this.pos, this.input));
                atLineStart = false;
            }
        }
        return tokens;
    }

    /**
     * True if the line ending at the newline at {@code newlineIndex} holds only spaces and tabs.
     */
    private boolean isBlankLine(int newlineIndex) {
        for (int i = newlineIndex - 1; i >= 0; i--) {
            char c = this.input.charAt(i);
            if (c == '\n') {
                return true;
            }
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the line starting at {@code lineStart} begins a new block: it is blank, or it starts with a heading,
     * quote or bullet marker. Uses the same rules as the marker branches in {@link #tokenize()}.
     */
    private boolean startsBlock(int lineStart) {
        if (lineStart >= this.length) {
            return false;
        }
        int afterSpaces = this.skipSpaces(lineStart);
        if (afterSpaces < this.length && this.input.charAt(afterSpaces) == '\n') {
            return true;
        }
        return this.headingLevelAt(lineStart) > 0 || this.isQuoteAt(lineStart) || this.isBulletAt(lineStart);
    }

    /**
     * The heading level (1 to 6) if a heading marker - one to six '#' followed by a space - starts at {@code at},
     * otherwise 0.
     */
    private int headingLevelAt(int at) {
        int level = 0;
        while (at + level < this.length && this.input.charAt(at + level) == '#') {
            level++;
            if (level > 6) {
                return 0;
            }
        }
        if (level == 0 || at + level >= this.length || this.input.charAt(at + level) != ' ') {
            return 0;
        }
        return level;
    }

    /**
     * A '>' followed by a space, a newline or the end of the input.
     */
    private boolean isQuoteAt(int at) {
        if (at >= this.length || this.input.charAt(at) != '>') {
            return false;
        }
        return at + 1 >= this.length || this.input.charAt(at + 1) == ' ' || this.input.charAt(at + 1) == '\n';
    }

    /**
     * A '-' or '*' followed by a space.
     */
    private boolean isBulletAt(int at) {
        if (at + 1 >= this.length) {
            return false;
        }
        char c = this.input.charAt(at);
        return (c == '-' || c == '*') && this.input.charAt(at + 1) == ' ';
    }

    private int skipSpaces(int from) {
        int i = from;
        while (i < this.length && (this.input.charAt(i) == ' ' || this.input.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private boolean peekMatch(String target) {
        if (this.pos + target.length() > this.length) {
            return false;
        }
        for (int i = 0; i < target.length(); i++) {
            if (this.input.charAt(this.pos + i) != target.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds target on the current line only. Returns -1 if a newline or the end of input comes first.
     */
    private int indexOf(char target, int startFrom) {
        for (int i = startFrom; i < this.length; i++) {
            char c = this.input.charAt(i);
            if (c == target) {
                return i;
            }
            if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    private boolean matchIgnoreCase(String target) {
        if (this.pos + target.length() > this.length) {
            return false;
        }
        for (int i = 0; i < target.length(); i++) {
            if (Character.toLowerCase(this.input.charAt(this.pos + i)) != target.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNotSpecial(char c) {
        return c != '\\' && c != '\n' && c != '#' && c != '>' && c != '*' && c != '_' && c != '~' && c != '<' && c != '[' && c != ']' && c != ')';
    }
}
