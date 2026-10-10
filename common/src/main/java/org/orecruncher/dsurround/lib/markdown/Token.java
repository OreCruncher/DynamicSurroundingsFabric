package org.orecruncher.dsurround.lib.markdown;

/**
 * A lexical token: a range of {@code source} plus an optional argument (the color text of a {@code <color:...>}
 * tag, or the level of a heading).
 */
record Token(TokenType type, int start, int end, CharSequence source, String argument) {
    public Token(TokenType type, int start, int end, CharSequence source) {
        this(type, start, end, source, null);
    }

    public CharSequence value() {
        return this.source.subSequence(this.start, this.end);
    }

    /**
     * Shows only this token's text. The generated version would print the whole source document.
     */
    @Override
    public String toString() {
        return this.type + "[" + this.value() + (this.argument == null ? "" : ", " + this.argument) + "]";
    }
}
