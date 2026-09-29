package org.orecruncher.dsurround.lib.markdown;

import java.util.ArrayList;
import java.util.List;

class Optimizer {
    public static ComponentNode optimize(ComponentNode root) {
        List<ComponentNode> optimizedChildren = new ArrayList<>();
        ComponentNode currentMerged = null;

        for (ComponentNode child : root.children) {
            optimize(child);

            if (currentMerged == null) {
                currentMerged = child;
                optimizedChildren.add(currentMerged);
            } else {
                // Do not merge if styles don't match, if child has its own sub-children,
                // OR if either node contains a newline character (to preserve line boundaries)
                boolean stylesMatch = currentMerged.style.matches(child.style);
                boolean hasChildren = !child.children.isEmpty();
                boolean containsNewline = currentMerged.text.indexOf("\n") != -1 || child.text.indexOf("\n") != -1;

                if (stylesMatch && !hasChildren && !containsNewline) {
                    currentMerged.text.append(child.text);
                } else {
                    currentMerged = child;
                    optimizedChildren.add(currentMerged);
                }
            }
        }

        root.children.clear();
        root.children.addAll(optimizedChildren);
        return root;
    }
}