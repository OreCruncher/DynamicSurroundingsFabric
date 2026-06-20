package org.orecruncher.dsurround.effects.particles;

import net.minecraft.resources.Identifier;

/** Compatibility holder retained for older Dynamic Surroundings particle code. */
public final class DsurroundParticleRenderType {
    private final Identifier texture;

    public DsurroundParticleRenderType(final Identifier texture) {
        this.texture = texture;
    }

    public Identifier getTexture() {
        return this.texture;
    }

    @Override
    public String toString() {
        return this.texture.toString();
    }
}
