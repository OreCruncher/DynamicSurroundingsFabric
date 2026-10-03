package org.orecruncher.dsurround.config.libraries;

import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

/**
 * A library built from the mod's configuration files and the game's data. Libraries are reloaded together when
 * resources, tags or the configuration change, and are used on the client thread unless their interface says
 * otherwise.
 */
public interface ILibrary extends IDebug {

    void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope);

    /**
     * Goes up whenever what the library would answer may have changed (a reload, and for some libraries other
     * events such as a new connection). Anything that caches the library's answers can compare it to know when to
     * work them out again.
     */
    int getVersion();
}
