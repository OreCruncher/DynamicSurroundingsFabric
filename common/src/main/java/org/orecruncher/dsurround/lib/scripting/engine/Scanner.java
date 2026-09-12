package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.*;

class Scanner {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!this.isAtEnd()) {
            // We are at the beginning of the next lexeme.
            this.start = this.current;
            this.scanToken();
        }

        this.tokens.add(Token.from(EOF, "", null, this.line, this.start));
        return this.tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(':
                this.addToken(LEFT_PAREN);
                break;
            case ')':
                this.addToken(RIGHT_PAREN);
                break;
            case ',':
                this.addToken(COMMA);
                break;
            case '.':
                this.addToken(DOT);
                break;
            case '-':
                this.addToken(MINUS);
                break;
            case '+':
                this.addToken(PLUS);
                break;
            case '*':
                this.addToken(STAR);
                break;
            case '!':
                this.addToken(this.match('=') ? NOT_EQUAL : NOT);
                break;
            case '=':
                if (!this.match('=')) {
                    ScriptException.throwException(this.line, this.current, "Unexpected character '%c'".formatted(this.peek()));
                }
                this.addToken(EQUAL_EQUAL);
                break;
            case '<':
                this.addToken(this.match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                this.addToken(this.match('=') ? GREATER_EQUAL : GREATER);
                break;
            case '|':
                if (!this.match('|')) {
                    ScriptException.throwException(this.line, this.current, "Unexpected character '%c'".formatted(this.peek()));
                }
                this.addToken(CONDITIONAL_OR);
                break;
            case '&':
                if (!this.match('&')) {
                    ScriptException.throwException(this.line, this.current, "Unexpected character '%c'".formatted(this.peek()));
                }
                this.addToken(CONDITIONAL_AND);
                break;
            case '/':
                if (this.match('/')) {
                    // A comment goes until the end of the line.
                    while (this.peek() != '\n' && !this.isAtEnd())
                        this.advance();
                } else {
                    this.addToken(SLASH);
                }
                break;

            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            case '\n':
                this.line++;
                break;

            case '"':
                this.string('"');
                break;

            case '\'':
                this.string('\'');
                break;

            default:
                if (this.isDigit(c)) {
                    this.number();
                } else if (this.isAlpha(c)) {
                    this.identifier();
                } else {
                    ScriptException.throwException(this.line, this.current,"Unexpected character.");
                }
                break;
        }
    }

    private void identifier() {
        // Identifiers can be joined together with a dot (think namespace).
        boolean afterDot = false;
        for (;;) {
            var peeked = this.peek();
            if (afterDot) {
                if (this.isAlpha(peeked)) {
                    afterDot = false;
                    this.advance();
                } else {
                    if (peeked == '\0')
                        ScriptException.throwException(this.line, this.current,"Unexpected end of line");
                    else
                        ScriptException.throwException(this.line, this.current, "Unexpected character '%c'".formatted(peeked));
                }
            } else if (peeked == '.') {
                afterDot = true;
                this.advance();
            } else if (this.isAlphaNumeric(peeked)) {
                this.advance();
            } else {
                break;
            }
        }

        // See if the identifier is a reserved word.
        String text = this.source.substring(this.start, this.current);

        TokenType type = Definitions.KEYWORDS.get(text);
        if (type == null)
            type = IDENTIFIER;
        this.addToken(type);
    }

    private void number() {
        while (this.isDigit(this.peek()))
            this.advance();

        // Look for a fractional part.
        if (this.peek() == '.' && this.isDigit(this.peekNext())) {
            // Consume the "."
            do this.advance();
            while (this.isDigit(this.peek()));
        }

        this.addToken(NUMBER, Double.parseDouble(this.source.substring(this.start, this.current)));
    }

    private void string(char closingChar) {
        while (this.peek() != closingChar && !this.isAtEnd()) {
            if (this.peek() == '\n')
                this.line++;
            this.advance();
        }

        // Unterminated string.
        if (this.isAtEnd()) {
            ScriptException.throwException(this.line, this.current, "Unterminated string.");
            return;
        }

        // The closing ".
        this.advance();

        // Trim the surrounding quotes.
        String value = this.source.substring(this.start + 1, this.current - 1);
        this.addToken(STRING, value);
    }

    private boolean match(char expected) {
        if (this.isAtEnd())
            return false;
        if (this.source.charAt(this.current) != expected)
            return false;

        this.current++;
        return true;
    }

    private char peek() {
        if (this.isAtEnd())
            return '\0';
        return this.source.charAt(this.current);
    }

    private char peekNext() {
        if (this.current + 1 >= this.source.length())
            return '\0';
        return this.source.charAt(this.current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return this.isAlpha(c) || this.isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAtEnd() {
        return this.current >= this.source.length();
    }

    private char advance() {
        this.current++;
        return this.source.charAt(this.current - 1);
    }

    private void addToken(TokenType type) {
        this.addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = this.source.substring(this.start, this.current);
        this.tokens.add(Token.from(type, text, literal, this.line, this.start));
    }
}