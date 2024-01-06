package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Predicate;

public abstract class AbstractEffectSystem implements IEffectSystem {

    protected final static ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    protected final IModLog logger;
    protected final Configuration config;
    protected final String systemName;

    protected final Long2ObjectOpenHashMap<IBlockEffect> systems = new Long2ObjectOpenHashMap<>();

    protected AbstractEffectSystem(IModLog logger, Configuration config, String systemName) {
        this.logger = logger;
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
            this.logger.debug("[%s] clearing %d effects", this.systemName, this.systems.size());
            this.systems.values().forEach(IBlockEffect::remove);
            this.systems.clear();
        }
    }

    @Override
    public abstract void blockScan(Level world, BlockState state, BlockPos pos);

    @Override
    public void blockUnscan(Level world, BlockState state, BlockPos pos) {
        if (this.systems.isEmpty())
            return;
        var longPos = pos.asLong();
        var effect = this.systems.get(longPos);
        if (effect != null) {
            effect.remove();
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
}
