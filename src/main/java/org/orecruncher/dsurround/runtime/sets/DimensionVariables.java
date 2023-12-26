package org.orecruncher.dsurround.runtime.sets;

import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionType;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class DimensionVariables extends VariableSet<IDimensionVariables> implements IDimensionVariables {

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
            final DimensionType dim = world.getDimension();
            final Identifier location = world.getRegistryKey().getValue();
            this.id = location.toString();
            this.hasSky = dim.hasSkyLight();
            this.name = location.getPath();
            this.isSuperFlat = WorldUtils.isSuperFlat(world);
        } else {
            this.id = "UNKNOWN";
            this.hasSky = false;
            this.name = "UNKNOWN";
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