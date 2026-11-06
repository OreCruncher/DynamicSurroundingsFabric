// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.eventing;

import org.orecruncher.dsurround.lib.events.EventPhase;
import org.orecruncher.dsurround.lib.events.EventPhases;
import org.orecruncher.dsurround.lib.events.EventingFactory;
import org.orecruncher.dsurround.lib.events.GenerateInvoker;
import org.orecruncher.dsurround.lib.events.IPhasedEvent;

/**
 * Fired once a frame when the level's translucent things have been drawn, outside any render pass, for what the mod
 * draws over the world. Everything goes to the main target, and the phases keep the order of the layers: a handler
 * registers with the phase it belongs to. Render thread only.
 * <p>
 * With Improved Transparency this comes after its composite, and the main target's depth doesn't have the translucent
 * things in it, so what is drawn here is in front of those it is behind.
 */
@GenerateInvoker
@FunctionalInterface
public interface IWorldRender {

    /** The scene's depth is copied here, for what reads it, and the particles' lights drawn */
    EventPhase PARTICLE_LIGHTS = EventPhase.of("world.particle_lights");
    EventPhase WINDOW_FROST = EventPhase.of("world.window_frost");
    EventPhase CLOUDS = EventPhase.of("world.clouds");
    EventPhase GROUND_FOG = EventPhase.of("world.ground_fog");
    EventPhase WEATHER = EventPhase.of("world.weather");
    EventPhase LIGHTNING = EventPhase.of("world.lightning");

    EventPhases PHASES = EventPhase.phaseOrderingOf(PARTICLE_LIGHTS, WINDOW_FROST, CLOUDS, GROUND_FOG, WEATHER, EventPhase.DEFAULT, LIGHTNING);

    IPhasedEvent<IWorldRender> EVENT = EventingFactory.createPhasedEvent(PHASES, IWorldRenderInvoker::create);

    void onRenderWorld();
}
