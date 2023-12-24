package org.orecruncher.dsurround.processing.misc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.BlockPosUtil;

import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public class BlockEffectManager        {

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
            final double range = this.blockEffectRange;
            final BlockPos min = BlockPos.ofFloored(current.getX() - range, current.getY() - range, current.getZ() - range);
            final BlockPos max = BlockPos.ofFloored(current.getX() + range, current.getY() + range, current.getZ() + range);

            pred = system -> {
                if (BlockPosUtil.notContains(system.getPos(), min, max)) {
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