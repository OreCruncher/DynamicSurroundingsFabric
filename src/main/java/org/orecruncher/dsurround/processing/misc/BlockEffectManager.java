package org.orecruncher.dsurround.processing.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.orecruncher.dsurround.effects.IBlockEffect;

import java.util.function.Predicate;

public class BlockEffectManager {

    private static final Predicate<IBlockEffect> STANDARD = system -> {
        system.tick();
        return system.isDone();
    };

    protected final int blockEffectRange;

    public BlockEffectManager(int blockEffectRange) {
        this.blockEffectRange = blockEffectRange;
    }

    private final Long2ObjectOpenHashMap<IBlockEffect> systems = new Long2ObjectOpenHashMap<>(512);
    private BlockPos lastPos = BlockPos.ORIGIN;

    public void tick(PlayerEntity player) {
        final BlockPos current = player.getBlockPos();
        final boolean sittingStill = this.lastPos.equals(current);
        this.lastPos = current;

        Predicate<IBlockEffect> pred = STANDARD;

        if (!sittingStill) {
            final int range = this.blockEffectRange;
            final BlockPos min = current.add(-range, -range, -range);
            final BlockPos max = current.add(range, range, range);
            final Box area = Box.enclosing(min, max);

            pred = system -> {
                if (!area.contains(system.getPosition())) {
                    system.setDone();
                } else {
                    system.tick();
                }
                return system.isDone();
            };
        }

        this.systems.values().removeIf(pred);
    }

    // Determines if it is OK to spawn a particle system at the specified
    // location. Generally only a single system can occupy a block.
    public boolean okToSpawn(final BlockPos pos) {
        return !this.systems.containsKey(pos.asLong());
    }

    public void add(final IBlockEffect system) {
        this.systems.put(system.getPosIndex(), system);
    }

    public int count() {
        return this.systems.size();
    }
}