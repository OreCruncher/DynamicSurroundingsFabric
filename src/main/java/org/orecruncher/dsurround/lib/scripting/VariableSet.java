package org.orecruncher.dsurround.lib.scripting;

/**
 * A VariableSet is used to insert instances into the JavaScript runtime environment so that scripts can access game
 * and mod data safely.  For example, data related to the player can be encapsulated into a player data variable set,
 * and have that data updated once per tick.  This ticking allows for the calculation and caching of values that are
 * expensive to calculate and reused repeatedly through the tick.
 *
 * @param <T>
 */
public abstract class VariableSet<T> {


    private final String setName;

    protected VariableSet(final String setName) {
        this.setName = setName;
    }


    public String getSetName() {
        return this.setName;
    }

    public void update(IVariableAccess varAccess) {

    }

    /**
     * Produces a class instance that will be inserted into the JavaScript runtime so that scripts can access.  The
     * class should only have accessors on the interface and avoid state changing methods.
     *
     * @return Instance that can be registered with the JavaScript engine
     */

    public abstract T getInterface();

}