package org.orecruncher.dsurround.lib.markdown;

/**
 * A run of text that all has the same {@link Style}. The parser's output is a flat list of these, in reading order.
 */
record Segment(String text, Style style) {
}