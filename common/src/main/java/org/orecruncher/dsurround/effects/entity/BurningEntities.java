package org.orecruncher.dsurround.effects.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * The entities whose fire {@link BurningEffect} draws, by id, so vanilla's fire sheets are left off for them and only
 * them (the render hook in {@code MixinEntityRenderDispatcher} asks). Everyone else, past the effect range for
 * instance, keeps vanilla's. Client thread only: effects tick and entities render there.
 */
public final class BurningEntities {

    private static final IntOpenHashSet IDS = new IntOpenHashSet();

    private BurningEntities() {
    }

    static void add(int entityId) {
        IDS.add(entityId);
    }

    static void remove(int entityId) {
        IDS.remove(entityId);
    }

    /**
     * Whether the mod draws this entity's fire in place of vanilla's
     */
    public static boolean drawsFireOf(int entityId) {
        return !IDS.isEmpty() && IDS.contains(entityId);
    }

    /**
     * How many entities' fire the mod draws, for diagnostics
     */
    public static int count() {
        return IDS.size();
    }
}
