package org.orecruncher.dsurround.eventing;

import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

public final class ClientEventHooks {
    /**
     * Used to collect diagnostic information for display in the debug HUD
     */
    public static final IPhasedEvent<CollectDiagnosticsEvent> COLLECT_DIAGNOSTICS = EventingFactory.createPrioritizedEvent();

    /**
     * Fired when block state updates are received clientside.  Results are coalesced for efficiency.
     */
    public static final IPhasedEvent<BlockUpdateEvent> BLOCK_UPDATE = EventingFactory.createPrioritizedEvent();

    /**
     * Fired when an Entity is detected as generating a step sound.
     */
    public static final IPhasedEvent<EntityStepEvent> ENTITY_STEP_EVENT = EventingFactory.createPrioritizedEvent();

}
