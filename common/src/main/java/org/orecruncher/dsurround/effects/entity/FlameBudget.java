package org.orecruncher.dsurround.effects.entity;

/**
 * Shares out tongues of flame among burning entities, so a crowd (undead burning at dawn) thins out evenly rather
 * than costing a flame jet's worth for each. Each tick every burning entity asks for what it would like; if, last tick,
 * they asked for more than {@link #perTick} in all, each gets the same fraction of its ask. Measuring last tick's
 * asks rather than this tick's lets the first entity ticked get its share without waiting for the rest; a crowd
 * changes slowly enough for that to be close.
 */
final class FlameBudget {

    private final float perTick;
    private long tick = Long.MIN_VALUE;
    private float asked;
    private float askedLastTick;

    FlameBudget(float perTick) {
        this.perTick = perTick;
    }

    /**
     * How many of {@code wanted} tongues an entity may have this tick (a fraction: the caller rounds).
     *
     * @param now the client tick count
     */
    float share(long now, float wanted) {
        if (now != this.tick) {
            // Only a tick straight after the last carries its asks over; after a gap nobody was burning
            this.askedLastTick = now == this.tick + 1 ? this.asked : 0F;
            this.asked = 0F;
            this.tick = now;
        }
        this.asked += wanted;
        float demand = Math.max(this.askedLastTick, this.asked);
        return demand <= this.perTick ? wanted : wanted * this.perTick / demand;
    }
}
