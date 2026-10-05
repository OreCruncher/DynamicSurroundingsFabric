package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * Merges neighboring segments that have the same style, so the output has as few components as possible.
 */
final class Optimizer {

    private Optimizer() {
    }

    /**
     * Returns a new list in which adjacent segments with equal styles are joined. Segments containing a newline are
     * never merged with anything, which keeps line boundaries as separate components.
     */
    static List<Segment> optimize(List<Segment> segments) {
        List<Segment> merged = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Style style = null;
        boolean runHasNewline = false;

        for (Segment segment : segments) {
            boolean hasNewline = segment.text().indexOf('\n') != -1;
            boolean canMerge = style != null && !runHasNewline && !hasNewline && style.equals(segment.style());

            if (canMerge) {
                // Neither the run nor the segment has a newline, so the merged run still doesn't
                text.append(segment.text());
            } else {
                // Close the current run and start a new one. A run containing a newline is never extended, so it
                // holds exactly the one segment that started it.
                if (style != null) {
                    merged.add(new Segment(text.toString(), style));
                }
                text.setLength(0);
                text.append(segment.text());
                style = segment.style();
                runHasNewline = hasNewline;
            }
        }

        if (style != null) {
            merged.add(new Segment(text.toString(), style));
        }
        return merged;
    }
}