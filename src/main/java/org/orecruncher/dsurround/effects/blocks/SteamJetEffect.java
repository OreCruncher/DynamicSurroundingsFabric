package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.blocks.producers.SteamColumnProducer;
import org.orecruncher.dsurround.lib.GameUtils;

public class SteamJetEffect extends ParticleJetEffect {

    private final BlockState source;

    public SteamJetEffect(final int strength, final World world, final double x, final double y, final double z) {
        super(strength, world, x, y, z);
        this.source = world.getBlockState(getPos());
    }

    @Override
    public boolean shouldDie() {
        var world = GameUtils.getWorld();
        return world.map(clientWorld -> !SteamColumnProducer.isValidSpawnBlock(clientWorld, this.getPos(), this.source)).orElse(true);
    }

    @Override
    protected void spawnJetParticle() {
        this.addParticle(ParticleTypes.CLOUD, this.posX, this.posY, this.posZ, 0, 0.1D, 0D);
    }

}