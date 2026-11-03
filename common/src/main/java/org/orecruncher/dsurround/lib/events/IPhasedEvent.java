// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.events;

public interface IPhasedEvent<THandler> extends IEvent<THandler> {

    /**
     * Registers a callback handler for the specified phase of the event.
     *
     * @param handler Handler to register with the event implementation
     * @param phase   Phase of event processing to register the handler with
     */
    void register(THandler handler, EventPhase phase);
}
