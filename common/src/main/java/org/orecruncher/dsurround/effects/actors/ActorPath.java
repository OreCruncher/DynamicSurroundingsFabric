// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

/**
 * An actor's whole life, worked out from its seed: how long it lasts and where it is at any age, so a player arriving
 * part way through finds it where everyone else does.
 */
public interface ActorPath {

    /**
     * How long it lasts, in ticks
     */
    int lifetime();

    /**
     * Where it is at an age, in ticks
     */
    double x(float age);

    double z(float age);
}
