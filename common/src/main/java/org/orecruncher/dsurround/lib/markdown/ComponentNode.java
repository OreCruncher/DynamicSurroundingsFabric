package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

record ComponentNode(StringBuilder text, Style style, List<ComponentNode> children) {
    public static ComponentNode create(Style style) {
        return new ComponentNode(new StringBuilder(), style, new ArrayList<>());
    }

    public void append(CharSequence seq, int start, int end) {
        for (int i = start; i < end; i++) {
            this.text.append(seq.charAt(i));
        }
    }

    public int indexOf(String seq) {
        return this.text.indexOf(seq);
    }

    public void append(char c) {
        this.text.append(c);
    }

    public String getText() {
        return this.text.toString();
    }

    public void merge(ComponentNode node) {
        this.text.append(node.text);
    }
}