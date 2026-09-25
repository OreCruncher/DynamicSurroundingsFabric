package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public final class DimensionVariables extends VariableSet {

    private final ILevelOracle levelOracle;

    private String id;
    private String name;
    private boolean hasSky;
    private boolean isSuperFlat;

    public DimensionVariables(ILevelOracle levelOracle) {
        super("dim");
        this.levelOracle = levelOracle;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            this.id = this.levelOracle.dimensionIdentifier().toString();
            this.name = this.levelOracle.dimensionName();
            this.hasSky = this.levelOracle.hasSkyLight();
            this.isSuperFlat = this.levelOracle.isSuperFlat();
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