package org.orecruncher.dsurround.config.libraries;

import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

@FunctionalInterface
public interface IReloadEvent {

    enum Scope {
        // Result of a tag sync
        TAGS,
        // Result of a resource reload
        RESOURCES,
        // Non-specific; reload of everything
        ALL
    }

    void onReload(ResourceUtilities resourceUtilities, Scope scope);
}
