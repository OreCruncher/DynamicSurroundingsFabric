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

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        boolean atLineStart = true;
        boolean inHeader = false; // true from a heading marker until the end of that line

        while (this.pos < this.length) {
            char c = this.input.charAt(this.pos);

            if (c == '\\' && this.pos + 1 < this.length && !(inHeader && this.input.charAt(this.pos + 1) == '\n')) {
                tokens.add(new Token(TokenType.TEXT, this.pos + 1, this.pos + 2, this.input));
                this.pos += 2;
                atLineStart = false;
            } else if (c == '\n') {
                int newlineStart = this.pos;
                this.pos++;
                atLineStart = true;
                boolean wasHeader = inHeader;
                inHeader = false;

                // Check if the current line being ended is a blank line (only whitespace since last newline)
                boolean isBlankLine = true;
                int lookBack = newlineStart - 1;
                while (lookBack >= 0 && this.input.charAt(lookBack) != '\n') {
                    char prevC = this.input.charAt(lookBack);
                    if (prevC != ' ' && prevC != '\t' && prevC != '\r') {
                        isBlankLine = false;
                        break;
                    }
                    lookBack--;
                }

                boolean isBlockBoundary = false;
                if (this.pos < this.length) {
                    char nextC = this.input.charAt(this.pos);
                    if (nextC == '\n' || nextC == '#' || nextC == '>') {
                        isBlockBoundary = true;
                    } else if ((nextC == '-' || nextC == '*') && this.pos + 1 < this.length && this.input.charAt(this.pos + 1) == ' ') {
                        isBlockBoundary = true;
                    }
                }

                if (isBlankLine || isBlockBoundary || wasHeader) {
                    tokens.add(new Token(TokenType.NEWLINE, newlineStart, newlineStart + 1, this.input));
                } else {
                    tokens.add(new Token(TokenType.SOFT_BREAK, newlineStart, newlineStart + 1, this.input));
                }
            } else if (atLineStart && c == '#') {
                int headerStart = this.pos;
                int hashCount = 0;
                while (this.pos < this.length && this.input.charAt(this.pos) == '#' && hashCount < 6) {
                    hashCount++;
                    this.pos++;
                }
                if (this.pos < this.length && this.input.charAt(this.pos) == ' ') {
                    this.pos++; // consume space
                    tokens.add(new Token(TokenType.HEADER_MARKER, headerStart, this.pos, this.input, String.valueOf(hashCount)));
                    // The rest of the line is scanned normally so inline markup works inside headings. The newline
                    // that ends the line is always a block boundary (see the '\n' branch above).
                    inHeader = true;
                    atLineStart = false;
                } else {
                    this.pos = headerStart;
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
            } else if (atLineStart && c == '>' && (this.pos + 1 >= this.length || this.input.charAt(this.pos + 1) == ' ' || this.input.charAt(this.pos + 1) == '\n')) {
                tokens.add(new Token(TokenType.BLOCKQUOTE_MARKER, this.pos, this.pos + 1, this.input));
                this.pos++;
                if (this.pos < this.length && this.input.charAt(this.pos) == ' ') {
                    this.pos++;
                }
            } else if (atLineStart && (c == '-' || c == '*') && this.pos + 1 < this.length && this.input.charAt(this.pos + 1) == ' ') {
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
                    String colorHex = this.input.subSequence(this.pos + 7, endIdx).toString().trim();
                    tokens.add(new Token(TokenType.COLOR_START, this.pos, endIdx + 1, this.input, colorHex));
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