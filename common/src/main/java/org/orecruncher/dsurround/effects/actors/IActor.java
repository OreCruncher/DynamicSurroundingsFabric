// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.client.multiplayer.ClientLevel;
import org.orecruncher.dsurround.config.ActorKind;

/**
 * A world actor: something that moves through the world on its own for a while, such as a dust devil. Client side
 * only, with no entity behind it; ticked on the client thread by the world actor handler, which drops it when it is
 * done or the player has left it behind.
 */
public interface IActor {

    ActorKind kind();

    /**
     * Moves it on a tick and makes what it shows
     *
     * @param particles how many particles it may make, 0 to 1, from the game's Particles option
     * @return whether it goes on; false once it is done
     */
    boolean tick(ClientLevel level, float particles);

    /**
     * Where it is now
     */
    double x();

    double z();
}
