package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.processing.Scanners;

public class EnvironmentState extends VariableSet {

    private final Scanners scanner;

    public EnvironmentState(Scanners scanner) {
        super("state");
        this.scanner = scanner;
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isInVillage"), 0, l -> this.scanner.isInVillage());
        config.defineFunction(id("isInside"), 0, l -> this.scanner.isInside());
        config.defineFunction(id("isUnderwater"), 0, l -> this.scanner.isUnderwater());
    }
}
