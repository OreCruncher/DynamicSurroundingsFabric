package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.producers.SteamColumnProducer;
import org.orecruncher.dsurround.lib.GameUtils;

@Environment(EnvType.CLIENT)
public class SteamJetEffect extends ParticleJetEffect {

    private final BlockState source;

    public SteamJetEffect(final int strength, final World world, final double x, final double y, final double z) {
        super(strength, world, x, y, z);
        this.source = world.getBlockState(getPos());
    }

    @Override
    public boolean shouldDie() {
        return !SteamColumnProducer.isValidSpawnBlock(GameUtils.getWorld(), getPos(), this.source);
    }

    @Override
    protected void spawnJetParticle() {
        //MixinParticleManager manager = (MixinParticleManager) (Object) (GameUtils.getMC().particleManager);
        //var particle = STEAMCLOUD_FACTORY.createParticle(null, GameUtils.getWorld(), this.posX, this.posY, this.posZ, 0, 0.1D, 0D);
        //GameUtils.getMC().particleManager.addParticle(particle);
    }

}