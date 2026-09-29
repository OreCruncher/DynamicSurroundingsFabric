package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

class Scanner {
    private final CharSequence input;
    private int pos = 0;
    private final int length;

    public Scanner(CharSequence input) {
        this.input = input;
        this.length = input.length();
    }

    public List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        boolean atLineStart = true;

        while (pos < length) {
            char c = input.charAt(pos);

            if (c == '\\' && pos + 1 < length) {
                tokens.add(new Token(TokenType.TEXT, pos + 1, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == '\n') {
                int newlineStart = pos;
                pos++;
                atLineStart = true;

                // Check if the current line being ended is a blank line (only whitespace since last newline)
                boolean isBlankLine = true;
                int lookBack = newlineStart - 1;
                while (lookBack >= 0 && input.charAt(lookBack) != '\n') {
                    char prevC = input.charAt(lookBack);
                    if (prevC != ' ' && prevC != '\t' && prevC != '\r') {
                        isBlankLine = false;
                        break;
                    }
                    lookBack--;
                }

                boolean isBlockBoundary = false;
                if (pos < length) {
                    char nextC = input.charAt(pos);
                    if (nextC == '\n' || nextC == '#' || nextC == '>') {
                        isBlockBoundary = true;
                    } else if ((nextC == '-' || nextC == '*') && pos + 1 < length && input.charAt(pos + 1) == ' ') {
                        isBlockBoundary = true;
                    }
                }

                if (isBlankLine || isBlockBoundary) {
                    tokens.add(new Token(TokenType.NEWLINE, newlineStart, newlineStart + 1, input));
                } else {
                    tokens.add(new Token(TokenType.SOFT_BREAK, newlineStart, newlineStart + 1, input));
                }
            } else if (atLineStart && c == '#') {
                int headerStart = pos;
                int hashCount = 0;
                while (pos < length && input.charAt(pos) == '#' && hashCount < 6) {
                    hashCount++;
                    pos++;
                }
                if (pos < length && input.charAt(pos) == ' ') {
                    pos++; // consume space
                    tokens.add(new Token(TokenType.HEADER_MARKER, headerStart, pos, input, String.valueOf(hashCount)));

                    int textStart = pos;
                    while (pos < length && input.charAt(pos) != '\n') {
                        pos++;
                    }
                    if (textStart < pos) {
                        tokens.add(new Token(TokenType.TEXT, textStart, pos, input));
                    }
                    if (pos < length && input.charAt(pos) == '\n') {
                        tokens.add(new Token(TokenType.NEWLINE, pos, pos + 1, input));
                        pos++;
                        atLineStart = true;
                    }
                } else {
                    pos = headerStart;
                    int start = pos;
                    while (pos < length && !isSpecial(input.charAt(pos))) {
                        pos++;
                    }
                    if (start == pos) pos++;
                    tokens.add(new Token(TokenType.TEXT, start, pos, input));
                    atLineStart = false;
                }
            } else if (atLineStart && c == '>' && (pos + 1 >= length || input.charAt(pos + 1) == ' ' || input.charAt(pos + 1) == '\n')) {
                tokens.add(new Token(TokenType.BLOCKQUOTE_MARKER, pos, pos + 1, input));
                pos++;
                if (pos < length && input.charAt(pos) == ' ') pos++;
                atLineStart = true;
            } else if (atLineStart && (c == '-' || c == '*') && pos + 1 < length && input.charAt(pos + 1) == ' ') {
                tokens.add(new Token(TokenType.BULLET_MARKER, pos, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == '*' && peekMatch("**")) {
                tokens.add(new Token(TokenType.BOLD_MARKER, pos, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == '_' && peekMatch("__")) {
                tokens.add(new Token(TokenType.UNDERLINE_MARKER, pos, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == '*') {
                tokens.add(new Token(TokenType.ITALIC_MARKER, pos, pos + 1, input));
                pos++;
                atLineStart = false;
            } else if (c == '~' && peekMatch("~~")) {
                tokens.add(new Token(TokenType.STRIKE_MARKER, pos, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == '<' && input.subSequence(pos, Math.min(length, pos + 7)).toString().startsWith("<color:")) {
                int endIdx = indexOf('>', pos);
                if (endIdx != -1) {
                    String colorHex = input.subSequence(pos + 7, endIdx).toString().trim();
                    tokens.add(new Token(TokenType.COLOR_START, pos, endIdx + 1, input, colorHex));
                    pos = endIdx + 1;
                } else {
                    pos++;
                }
                atLineStart = false;
            } else if (c == '<' && input.subSequence(pos, Math.min(length, pos + 8)).toString().equalsIgnoreCase("</color>")) {
                tokens.add(new Token(TokenType.COLOR_END, pos, pos + 8, input));
                pos += 8;
                atLineStart = false;
            } else if (c == '[') {
                tokens.add(new Token(TokenType.LINK_START, pos, pos + 1, input));
                pos++;
                atLineStart = false;
            } else if (c == ']' && pos + 1 < length && input.charAt(pos + 1) == '(') {
                tokens.add(new Token(TokenType.LINK_MID, pos, pos + 2, input));
                pos += 2;
                atLineStart = false;
            } else if (c == ')') {
                tokens.add(new Token(TokenType.LINK_END, pos, pos + 1, input));
                pos++;
                atLineStart = false;
            } else {
                int start = pos;
                while (pos < length && !isSpecial(input.charAt(pos))) {
                    pos++;
                }
                if (start == pos) {
                    pos++;
                }
                tokens.add(new Token(TokenType.TEXT, start, pos, input));
                atLineStart = false;
            }
        }
        return tokens;
    }

    private boolean peekMatch(String target) {
        if (pos + target.length() > length) return false;
        for (int i = 0; i < target.length(); i++) {
            if (input.charAt(pos + i) != target.charAt(i)) return false;
        }
        return true;
    }

    private int indexOf(char target, int startFrom) {
        for (int i = startFrom; i < length; i++) {
            if (input.charAt(i) == target) return i;
        }
        return -1;
    }

    private boolean isSpecial(char c) {
        return c == '\\' || c == '\n' || c == '#' || c == '>' || c == '*' || c == '_' || c == '~' || c == '<' || c == '[' || c == ']' || c == ')';
    }
}