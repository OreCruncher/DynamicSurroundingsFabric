package org.orecruncher.dsurround.runtime.sets.impl;

import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.processing.Scanners;
import org.orecruncher.dsurround.runtime.sets.IEnvironmentState;

public class EnvironmentState extends VariableSet<IEnvironmentState> implements IEnvironmentState {

    private final Scanners scanner;

    public EnvironmentState(Scanners scanner) {
        super("state");
        this.scanner = scanner;
    }

    @Override
    public IEnvironmentState getInterface() {
        return this;
    }

    @Override
    public boolean isInVillage() {
        return this.scanner.isInVillage();
    }

    @Override
    public boolean isInside() {
        return this.scanner.isInside();
    }

    @Override
    public boolean isUnderWater() {
        return this.scanner.isUnderwater();
    }
}
