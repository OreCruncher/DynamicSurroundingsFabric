package org.orecruncher.dsurround.runtime.variables;

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

    public DimensionVariables() {
        super("dim");
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            var world = GameUtils.getWorld().orElseThrow();
            final DimensionType dim = world.dimensionType();
            this.id = world.dimension().identifier().toString();
            this.name = world.dimension().identifier().getPath();
            this.hasSky = dim.hasSkyLight();
            this.isSuperFlat = LevelCompat.isSuperFlat(world);
        } else {
            this.id = "UNKNOWN";
            this.name = "UNKNOWN";
            this.hasSky = false;
            this.isSuperFlat = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("getId"), l -> this.id);
        config.defineFunction(id("getDimName"), l -> this.name);
        config.defineFunction(id("hasSky"), l -> this.hasSky);
        config.defineFunction(id("isSuperFlat"), l -> this.isSuperFlat);
    }
}