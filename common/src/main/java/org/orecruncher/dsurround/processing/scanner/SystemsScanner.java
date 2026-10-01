package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.Cacheable;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scanner.CuboidScanner;
import org.orecruncher.dsurround.lib.scanner.ScanContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Cacheable
public class SystemsScanner extends CuboidScanner {

    private static final Predicate<IBlockEffect> EFFECT_PREDICATE = system -> {
        system.tick();
        return system.isDone();
    };

    private final Configuration config;
    private final ObjectArray<IEffectSystem> systems = new ObjectArray<>();
    // The enabled systems, worked out once per tick rather than for every scanned block. scanTargets leaves out
    // systems that don't want blockScan calls.
    private final List<IEffectSystem> scanTargets = new ArrayList<>();
    private final List<IEffectSystem> unscanTargets = new ArrayList<>();

    private int lastRange;
    // Where the player was last tick, for deciding whether effects need a range check. Kept separate from
    // CuboidScanner.lastPos, which records where the scanned volume is centered and must only change with it.
    private BlockPos lastPlayerPos = BlockPos.ZERO;

    public SystemsScanner(Configuration config, ScanContext locus) {
        super(locus, "SystemsScanner", config.blockEffects.blockEffectRange);

        this.config = config;
        this.lastRange = config.blockEffects.blockEffectRange;
    }

    public void addEffectSystem(IEffectSystem system) {
        this.systems.add(system);
        this.refreshTargets();
    }

    private void refreshTargets() {
        this.scanTargets.clear();
        this.unscanTargets.clear();
        for (var system : this.systems) {
            if (system.isEnabled()) {
                this.unscanTargets.add(system);
                if (system.wantsBlockScans())
                    this.scanTargets.add(system);
            }
        }
    }

    public void resetFullScan() {
        super.resetFullScan();
        this.systems.forEach(IEffectSystem::clear);
    }

    @Override
    public void tick() {
        // Systems can be switched on and off in the config while playing
        this.refreshTargets();
        super.tick();

        // If the range changed, we need to reset all effects in process
        if (this.lastRange != this.config.blockEffects.blockEffectRange) {
            this.lastRange = this.config.blockEffects.blockEffectRange;
            this.setRange(this.lastRange);
            return;
        }

        var player = GameUtils.getPlayer().orElseThrow();
        final BlockPos current = player.blockPosition();
        final boolean sittingStill = this.lastPlayerPos.equals(current);
        this.lastPlayerPos = current;

        Predicate<IBlockEffect> filter;

        if (!sittingStill) {
            var range = this.config.blockEffects.blockEffectRange;
            var blockBox = BlockBox.of(current.offset(-range, -range, -range), current.offset(range, range, range));

            filter = system -> {
                if (blockBox.contains(system.getPos())) {
                    system.tick();
                } else {
                    system.remove();
                }
                return system.isDone();
            };
        } else {
            filter = EFFECT_PREDICATE;
        }

        this.processIfEnabled(true, system -> system.tick(filter));
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

    // Called for every block scanned, so these are plain loops over the per-tick lists

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        for (int i = 0; i < this.scanTargets.size(); i++)
            this.scanTargets.get(i).blockScan(world, state, pos);
    }

    @Override
    public void blockUnscan(Level world, BlockState state, BlockPos pos, IRandomizer rand) {
        for (int i = 0; i < this.unscanTargets.size(); i++)
            this.unscanTargets.get(i).blockUnscan(world, state, pos);
    }

    public void gatherDiagnostics(Collection<Component> output) {
        output.add(Component.literal("[%s] pending blocks: %d".formatted(this.name, this.getPendingBlocks())));
        this.systems.forEach(system -> {
            var text = system.gatherDiagnostics();
            if (!system.isEnabled())
                text += " (disabled)";
            output.add(Component.literal(text));
        });
    }
}
