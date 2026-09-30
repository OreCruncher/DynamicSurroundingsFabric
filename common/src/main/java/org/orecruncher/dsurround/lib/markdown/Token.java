package org.orecruncher.dsurround.lib.markdown;

record Token(TokenType type, int start, int end, CharSequence source, String extraData) {
    public Token(TokenType type, int start, int end, CharSequence source) {
        this(type, start, end, source, null);
    }

    public CharSequence value() {
        return this.source.subSequence(this.start, this.end);
    }
}
