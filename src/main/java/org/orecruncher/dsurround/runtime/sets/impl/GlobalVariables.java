package org.orecruncher.dsurround.runtime.sets.impl;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.runtime.sets.IGlobalVariables;

public class GlobalVariables  extends VariableSet<IGlobalVariables> implements IGlobalVariables {

    private final Configuration config;

    public GlobalVariables(Configuration config) {
        super("global");

        this.config = config;
    }

    @Override
    public IGlobalVariables getInterface() {
        return this;
    }

    @Override
    public boolean allowScary() {
        return this.config.soundOptions.allowScarySounds;
    }
}
