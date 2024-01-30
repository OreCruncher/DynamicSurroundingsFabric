package org.orecruncher.dsurround.runtime.sets;

@SuppressWarnings("unused")
public interface ISeasonVariables {
    boolean isSpring();
    boolean isSummer();
    boolean isAutumn();
    boolean isWinter();

    default boolean isWarm() {
        return this.isSpring() || this.isSummer();
    }

    default boolean isCool() {
        return this.isAutumn() || this.isWinter();
    }
}
