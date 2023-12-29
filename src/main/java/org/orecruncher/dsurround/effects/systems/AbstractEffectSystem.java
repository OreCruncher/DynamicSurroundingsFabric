package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.mixins.core.MixinParticleManager;

import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractEffectSystem implements IEffectSystem {

    protected final static ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    protected final Configuration config;
    protected final String systemName;

    protected final Long2ObjectOpenHashMap<IBlockEffect> systems = new Long2ObjectOpenHashMap<>();

    protected AbstractEffectSystem(Configuration config, String systemName) {
        this.config = config;
        this.systemName = systemName;
    }

    public abstract boolean isEnabled();

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        this.systems.values().removeIf(processingPredicate);
    }

    @Override
    public void clear() {
        if (!this.systems.isEmpty()) {
            this.systems.values().forEach(IBlockEffect::setDone);
            this.systems.clear();
        }
    }

    @Override
    public abstract void blockScan(World world, BlockState state, BlockPos pos);

    @Override
    public void blockUnscan(World world, BlockState state, BlockPos pos) {
        if (this.systems.isEmpty())
            return;
        var longPos = pos.asLong();
        var effect = this.systems.get(longPos);
        if (effect != null) {
            effect.setDone();
            this.onRemoveSystem(longPos);
        }
    };

    @Override
    public String gatherDiagnostics() {
        return "[%s] count: %d".formatted(this.systemName, this.systems.size());
    }

    protected boolean hasSystemAtPosition(BlockPos pos) {
        return this.systems.containsKey(pos.asLong());
    }

    protected void onRemoveSystem(long posLong) {
        this.systems.remove(posLong);
    }

    protected <T extends ParticleEffect> Optional<Particle> createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        return GameUtils.getParticleManager().map(pm -> {
            var t = (MixinParticleManager) pm;
            return t.dsurroundCreateParticle(
                    parameters,
                    x, y, z,
                    velocityX, velocityY, velocityZ);
        });
    }
}
