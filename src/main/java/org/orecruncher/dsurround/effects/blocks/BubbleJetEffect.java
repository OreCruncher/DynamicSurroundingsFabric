package org.orecruncher.dsurround.effects.blocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class BubbleJetEffect extends ParticleJetEffect {

    public BubbleJetEffect(final int strength, final World world, final double x, final double y, final double z) {
        super(strength, world, x, y, z);
    }

    @Override
    protected void spawnJetParticle() {
        this.addParticle(ParticleTypes.BUBBLE, this.posX, this.posY, this.posZ, 0, 0.5D + this.jetStrength / 10D, 0D);
    }
}