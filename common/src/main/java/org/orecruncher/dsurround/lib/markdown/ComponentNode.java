package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

class ComponentNode {
    final StringBuilder text = new StringBuilder();
    final Style style;
    final List<ComponentNode> children = new ArrayList<>();

    public ComponentNode(Style style) {
        this.style = style;
    }

    public void append(CharSequence seq, int start, int end) {
        for (int i = start; i < end; i++) {
            text.append(seq.charAt(i));
        }
    }

    public void append(char c) {
        text.append(c);
    }

    public String getText() {
        return text.toString();
    }
}