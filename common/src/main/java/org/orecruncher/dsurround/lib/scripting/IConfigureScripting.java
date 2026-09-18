package org.orecruncher.dsurround.lib.scripting;

@FunctionalInterface
public interface IConfigureScripting {

    /**
     * Called by the Script engine to configure any function or variable definitions to add to the execution
     * environment.
     *
     * @param setup Reference to use when configuring functions and definitions
     */
    void configure(IConfigureDefinition setup);
}
