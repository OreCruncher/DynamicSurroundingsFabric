package org.orecruncher.dsurround.runtime.sets.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;
import org.orecruncher.dsurround.runtime.sets.IDimensionVariables;

public class DimensionVariables extends VariableSet<IDimensionVariables> implements IDimensionVariables {

    private static final IDimensionLibrary DIMENSION_LIBRARY = ContainerManager.resolve(IDimensionLibrary.class);

    private String id;
    private String name;
    private boolean hasSky;
    private boolean isSuperFlat;

    public DimensionVariables() {
        super("dim");
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
        } else {
            this.id = "UNKNOWN";
            this.name = "UNKNOWN";
            this.hasSky = false;
            this.isSuperFlat = false;
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
}