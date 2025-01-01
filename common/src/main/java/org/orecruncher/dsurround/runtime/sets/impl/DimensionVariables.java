package org.orecruncher.dsurround.runtime.sets.impl;

import net.minecraft.world.level.dimension.DimensionType;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;
import org.orecruncher.dsurround.runtime.sets.IDimensionVariables;

public class DimensionVariables extends VariableSet<IDimensionVariables> implements IDimensionVariables {

    private final IDimensionLibrary dimensionLibrary;
    private String id;
    private String name;
    private boolean hasSky;
    private boolean isSuperFlat;
    private int seaLevel;

    public DimensionVariables(IDimensionLibrary dimensionLibrary) {
        super("dim");
        this.dimensionLibrary = dimensionLibrary;
    }

    @Override
    public IDimensionVariables getInterface() {
        return this;
    }

    @Override
    public void update(IVariableAccess variableAccess) {
        if (GameUtils.isInGame()) {
            var world = GameUtils.getWorld().orElseThrow();
            final DimensionType dim = world.dimensionType();
            this.id = world.dimension().location().toString();
            this.name = world.dimension().location().getPath();
            this.hasSky = dim.hasSkyLight();
            this.isSuperFlat = WorldUtils.isSuperFlat(world);
            this.seaLevel = this.dimensionLibrary.getData(world).getSeaLevel();
        } else {
            this.id = "UNKNOWN";
            this.name = "UNKNOWN";
            this.hasSky = false;
            this.isSuperFlat = false;
            this.seaLevel = 63;
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getDimName() {
        return this.name;
    }

    @Override
    public boolean hasSky() {
        return this.hasSky;
    }

    @Override
    public boolean isSuperFlat() {
        return this.isSuperFlat;
    }

    @Override
    public int getSeaLevel() {
        return this.seaLevel;
    }
}