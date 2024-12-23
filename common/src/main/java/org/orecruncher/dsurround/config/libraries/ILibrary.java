package org.orecruncher.dsurround.config.libraries;

import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

public interface ILibrary extends IDebug {

    void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope);
}
