package org.orecruncher.dsurround.eventing;

import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

/**
 * Event raised when the mod's asset libraries need to reload: after a resource reload, a tag sync, or the reload
 * command.
 */
@GenerateInvoker
@FunctionalInterface
public interface IReloadEvent {

    IPhasedEvent<IReloadEvent> EVENT = EventingFactory.createPrioritizedEvent(IReloadEventInvoker::create);

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
