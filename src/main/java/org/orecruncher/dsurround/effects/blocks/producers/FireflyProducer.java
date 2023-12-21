package org.orecruncher.dsurround.effects.blocks.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.effects.particles.FireflyParticle;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.Random;

@Environment(EnvType.CLIENT)
public class FireflyProducer extends BlockParticleEffectProducer {
    public FireflyProducer(Script chance, Script conditions) {
        super(chance, conditions);
    }

    @Override
    protected void produceParticle(World world, BlockState state, BlockPos pos, Random rand) {
        var particle = new FireflyParticle(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ());
        this.addParticle(particle);
    }
}
