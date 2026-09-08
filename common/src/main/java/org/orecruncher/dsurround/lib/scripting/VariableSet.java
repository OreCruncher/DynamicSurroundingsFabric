package org.orecruncher.dsurround.lib.scripting;

/**
 * A VariableSet is used to insert instances into the scripting runtime environment so that scripts can access game
 * and mod data safely.  For example, data related to the player can be encapsulated into a player data variable set,
 * and have that data updated once per tick.  This ticking allows for the calculation and caching of values that are
 * expensive to calculate and reused repeatedly through the tick.
 */
public abstract class VariableSet implements IConfigureScripting {


    private final String setName;

    protected VariableSet(final String setName) {
        this.setName = setName;
    }


    public String getSetName() {
        return this.setName;
    }

    public void update(IVariableAccess varAccess) {

    }

    protected String id(String functionName) {
        return this.setName + '.' + functionName;
    }

    /**
     * Called by the scripting system to configure any functions or variables to be injected into the
     * scripting environment.
     * @param config
     */
    public abstract void configure(IConfigureDefinition config);
}