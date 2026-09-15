package org.orecruncher.dsurround.lib.scripting;

import org.orecruncher.dsurround.lib.ITickable;

/**
 * A VariableSet is used to insert instances into the scripting runtime environment so that scripts can access game
 * and mod data safely.  For example, data related to the player can be encapsulated into a player data variable set,
 * and have that data updated once per tick.  This ticking allows for the calculation and caching of values that are
 * expensive to calculate and reused repeatedly throughout the tick.
 */
public abstract class VariableSet implements IConfigureScripting, ITickable {

    private final String setName;

    protected VariableSet(final String setName) {
        this.setName = setName;
    }

    public final String getSetName() {
        return this.setName;
    }

    /**
     *  Called at the start of every client tick. Variable sets that need to reset or cache data for operations within
     *  the tick should override this method to implement such behavior.
     */
    public void tick() {

    }

    /**
     * Helper method to formulate a name for a variable or function by combining a root function name with
     * the variable set name to, in effect, put it in a namespace. (ex: "dim" with "getId" to return "dim.getId").
     * @param rootFunctionName Name of the function or variable to be defined
     * @return Full name of the function or variable name that has been modified by the set name
     */
    protected final String id(String rootFunctionName) {
        return this.setName + "." + rootFunctionName;
    }

    /**
     * Called by the scripting system to configure any functions or variables to be injected into the
     * scripting environment.
     * @param config Configuration sink that is used to define variables and functions within the scripting environment
     */
    public abstract void configure(IConfigureDefinition config);
}