package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;

import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SystemsScanner extends CuboidScanner {

    private static final Predicate<IBlockEffect> EFFECT_PREDICATE = system -> {
        system.tick();
        return system.isDone();
    };

    private final ObjectArray<IEffectSystem> systems = new ObjectArray<>();
    private final int range;

    public SystemsScanner(Configuration config, ScanContext locus) {
        super(locus, "SystemsScanner", config.blockEffects.blockEffectRange, 0);;

        this.range = config.blockEffects.blockEffectRange;
    }

    public void addEffectSystem(IEffectSystem system) {
        this.systems.add(system);
    }

    public void resetFullScan() {
        super.resetFullScan();
        this.systems.forEach(IEffectSystem::clear);
    }

    @Override
    public void tick() {
        super.tick();

        var player = GameUtils.getPlayer().orElseThrow();
        final BlockPos current = player.getBlockPos();
        final boolean sittingStill = this.lastPos.equals(current);
        this.lastPos = current;

        Predicate<IBlockEffect> pred;

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
        } else {
            pred = EFFECT_PREDICATE;
        }

        this.processIfEnabled(true, system -> system.tick(pred));
    }

    protected void processIfEnabled(boolean clearSystems, Consumer<IEffectSystem> systemConsumer) {
        for (var system : this.systems)
            if (system.isEnabled())
                systemConsumer.accept(system);
            else if(clearSystems)
                system.clear();
    }

    @Override
    public boolean doBlockUnscan() {
        return true;
    }

    @Override
    public void blockScan(World world, BlockState state, BlockPos pos, Random rand) {
        this.processIfEnabled(false, system -> system.blockScan(world, state, pos));
    }

    @Override
    public void blockUnscan(World world, BlockState state, BlockPos pos, Random rand) {
        this.processIfEnabled(false, system -> system.blockUnscan(world, state, pos));
    }

    public void gatherDiagnostics(Collection<String> output) {
        this.systems.forEach(system -> {
            var text = Formatting.LIGHT_PURPLE + system.gatherDiagnostics();
            if (!system.isEnabled())
                text += Formatting.RED + " (disabled)";
            output.add(text);
        });
    }
}
