package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public class GlobalVariables extends VariableSet {

    private final Configuration config;

    public GlobalVariables(Configuration config) {
        super("global");

        this.config = config;
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("allowScary"), 0, l -> this.config.soundOptions.allowScarySounds);
    }
}
