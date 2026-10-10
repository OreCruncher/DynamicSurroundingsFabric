package org.orecruncher.dsurround.lib.markdown;

enum TokenType {
    TEXT,
    /**
     * A single character made literal by a preceding backslash. The token covers the character, not the backslash.
     */
    ESCAPED,
    NEWLINE,
    SOFT_BREAK,
    BOLD_MARKER,
    ITALIC_MARKER,
    UNDERLINE_MARKER,
    STRIKE_MARKER,
    BLOCKQUOTE_MARKER,
    HEADER_MARKER,
    BULLET_MARKER,
    COLOR_START,
    COLOR_END,
    LINK_START,
    LINK_MID,
    LINK_END
}
