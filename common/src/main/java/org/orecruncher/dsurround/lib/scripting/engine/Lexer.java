package org.orecruncher.dsurround.lib.scripting.engine;

import com.google.common.annotations.VisibleForTesting;

import java.util.*;

import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.*;

@VisibleForTesting
class Lexer {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    // Absolute offset of the first character of the current line
    private int lineStart = 0;
    // Location where the token currently being scanned started
    private int tokenLine = 1;
    private int tokenColumn = 0;

    Lexer(String source) {
        this.source = source;
    }

    public List<Token> getTokens() {
        while (!this.isAtEnd()) {
            // We are at the beginning of the next lexeme.
            this.start = this.current;
            this.tokenLine = this.line;
            this.tokenColumn = this.current - this.lineStart;
            this.scanToken();
        }

        this.tokens.add(Token.from(EOF, "", null, this.line, this.current - this.lineStart, this.current));
        return this.tokens;
    }

    private void scanToken() {
        char c = advance();
        Token lastToken = this.tokens.isEmpty() ? null : this.tokens.getLast();
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
                // Member access on values is not supported. Dots are only valid inside identifiers (namespaces)
                // and numeric literals, both of which are consumed by their own scanners.
                this.errorAtTokenStart("Unexpected character '.'");
                break;
            case '-':
                // Need to disambiguate between the binary operator and the prefix operator for negation
                if (this.isPrefixPosition(lastToken)) {
                    this.addToken(NEG);
                } else {
                    this.addToken(MINUS);
                }
                break;
            case '+':
                // Similar rule to negation, but the + sign is dropped because a number without a negative
                // prefix is assumed to be positive.
                if (!this.isPrefixPosition(lastToken)) {
                    this.addToken(PLUS);
                }
                break;
            case '*':
                this.addToken(STAR);
                break;
            case '!':
                this.addToken(this.match('=') ? NOT_EQUAL : NOT);
                break;
            case '=':
                if (!this.match('=')) {
                    this.errorAtTokenStart("Unexpected character '=' (did you mean '=='?)");
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
                    this.errorAtTokenStart("Unexpected character '|' (did you mean '||'?)");
                }
                this.addToken(CONDITIONAL_OR);
                break;
            case '&':
                if (!this.match('&')) {
                    this.errorAtTokenStart("Unexpected character '&' (did you mean '&&'?)");
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
                this.newLine();
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
                    this.errorAtTokenStart("Unexpected character '%c'".formatted(c));
                }
                break;
        }
    }

    /**
     * A '-' or '+' is a prefix (unary) operator when it starts the expression, follows another operator, or
     * starts a parenthesized expression or function argument.
     */
    private boolean isPrefixPosition(Token lastToken) {
        return lastToken == null
                || lastToken.type().isOperator()
                || lastToken.type() == LEFT_PAREN
                || lastToken.type() == COMMA;
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
                        this.errorAtCurrent("Unexpected end of line");
                    else
                        this.errorAtCurrent("Unexpected character '%c'".formatted(peeked));
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

        try {
            this.addToken(NUMBER, Double.parseDouble(this.source.substring(this.start, this.current)));
        } catch (final NumberFormatException e) {
            this.errorAtTokenStart("Invalid numeric constant");
        }
    }

    private void string(char closingChar) {
        while (this.peek() != closingChar && !this.isAtEnd()) {
            char c = this.advance();
            if (c == '\n')
                this.newLine();
        }

        // Unterminated string.
        if (this.isAtEnd()) {
            this.errorAtTokenStart("Unterminated string");
            return;
        }

        // The closing ".
        this.advance();

        // Trim the surrounding quotes.
        String value = this.source.substring(this.start + 1, this.current - 1);
        this.addToken(STRING, value);
    }

    private void newLine() {
        // Called after the '\n' has been consumed
        this.line++;
        this.lineStart = this.current;
    }

    private void errorAtTokenStart(String message) {
        ScriptException.throwException(this.tokenLine, this.tokenColumn, this.start, message);
    }

    private void errorAtCurrent(String message) {
        ScriptException.throwException(this.line, this.current - this.lineStart, this.current, message);
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
        this.tokens.add(Token.from(type, text, literal, this.tokenLine, this.tokenColumn, this.start));
    }
}
