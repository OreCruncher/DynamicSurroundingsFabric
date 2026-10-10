package org.orecruncher.dsurround.lib.scripting;

/**
 * The arguments passed to a function handler. Each argument has already been converted to the type declared for
 * its parameter, so the typed getters below can be used directly, for example {@link #number(int)} for a
 * {@link ArgType#NUMBER} parameter.
 * <p>
 * For a lazy function, an argument is evaluated the first time it is requested and the result is reused.
 */
public abstract class ScriptArguments {

    protected ScriptArguments() {
    }

    /**
     * @return Number of arguments supplied in the script
     */
    public abstract int count();

    /**
     * @return The argument at the index, converted to its parameter type
     */
    public abstract Object value(int index);

    /** Argument declared as {@link ArgType#NUMBER}. */
    public double number(int index) {
        return ((Number) this.value(index)).doubleValue();
    }

    /** Argument declared as {@link ArgType#INTEGER}. */
    public int integer(int index) {
        return ((Number) this.value(index)).intValue();
    }

    /** Argument declared as {@link ArgType#BOOLEAN}. */
    public boolean bool(int index) {
        return (Boolean) this.value(index);
    }

    /** Argument declared as {@link ArgType#STRING}. */
    public String string(int index) {
        return (String) this.value(index);
    }

    /** Argument of a custom {@link ArgType}, such as a biome trait. */
    @SuppressWarnings("unchecked")
    public <T> T get(int index) {
        return (T) this.value(index);
    }

    /**
     * @return All arguments as an array (evaluating any lazy arguments not yet requested)
     */
    public Object[] toArray() {
        var result = new Object[this.count()];
        for (int i = 0; i < result.length; i++)
            result[i] = this.value(i);
        return result;
    }
}
