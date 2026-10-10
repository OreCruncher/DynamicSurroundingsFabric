package org.orecruncher.dsurround.runtime.variables;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public final class DimensionVariables extends VariableSet {

    private String id;
    private String name;
    private boolean hasSky;
    private boolean isSuperFlat;
    // The level the values were taken from; they only change with it
    private @Nullable Level level;

    public DimensionVariables() {
        super("dim");
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            var world = GameUtils.getWorld().orElseThrow();
            if (world == this.level)
                return;
            this.level = world;
            final DimensionType dim = world.dimensionType();
            this.id = world.dimension().identifier().toString();
            this.name = world.dimension().identifier().getPath();
            this.hasSky = dim.hasSkyLight();
            this.isSuperFlat = LevelCompat.isSuperFlat(world);
        } else {
            this.level = null;
            this.id = "UNKNOWN";
            this.name = "UNKNOWN";
            this.hasSky = false;
            this.isSuperFlat = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.property(id("getId"), () -> this.id);
        config.property(id("getDimName"), () -> this.name);
        config.property(id("hasSky"), () -> this.hasSky);
        config.property(id("isSuperFlat"), () -> this.isSuperFlat);
    }
}