package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;

import java.util.Random;
import java.util.function.Predicate;

public class AlwaysOnBlockEffectScanner extends CuboidScanner {

    private static final Predicate<IBlockEffect> STANDARD = system -> {
        system.tick();
        return system.isDone();
    };

    private final IBlockLibrary blockLibrary;
    private final Long2ObjectOpenHashMap<IBlockEffect> systems = new Long2ObjectOpenHashMap<>(512);
    private final int range;

    private BlockPos lastPos = BlockPos.ORIGIN;

    public AlwaysOnBlockEffectScanner(final ScanContext locus, IBlockLibrary blockLibrary, final int range) {
        super(locus, "AlwaysOnBlockEffectScanner", range, 0);

        this.blockLibrary = blockLibrary;
        this.range = range;
    }

    @Override
    public void tick() {
        // Do the super tick
        super.tick();

        var player = GameUtils.getPlayer().orElseThrow();
        final BlockPos current = player.getBlockPos();
        final boolean sittingStill = this.lastPos.equals(current);
        this.lastPos = current;

        Predicate<IBlockEffect> pred = STANDARD;

        if (!sittingStill) {
            final BlockPos min = current.add(-this.range, -this.range, -this.range);
            final BlockPos max = current.add(this.range, this.range, this.range);
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

    @Override
    public void blockScan(final BlockState state, final BlockPos pos, final Random rand) {
        var info = this.blockLibrary.getBlockInfo(state);
        if (!info.hasAlwaysOnEffects())
            return;

        var effects = info.getAlwaysOnEffectProducers();
        if (!effects.isEmpty() && this.okToSpawn(pos)) {
            final World world = this.locus.world().get();
            for (var be : effects) {
                var effect = be.produce(world, state, pos, rand);
                if (effect.isPresent()) {
                    this.add(effect.get());
                    // Only one effect per block
                    break;
                }
            }
        }
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