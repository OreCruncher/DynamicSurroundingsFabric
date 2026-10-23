package org.orecruncher.dsurround.effects.footprints;

/**
 * Works out when an entity's feet come down, from how far it moves along the ground each tick. On the client only the
 * player's own movement runs vanilla's step code; everyone else is moved to where the server says, so their steps
 * have to be worked out like this. Feet take turns; landing from a jump or a fall puts both down at once.
 */
final class StrideTracker {

    enum Step {
        NONE,
        /** One foot: {@link #rightFoot()} says which */
        ONE,
        /** Both feet: a landing */
        BOTH
    }

    // Off the ground this many ticks or more counts as a jump or a fall; fewer is just stepping up a block
    static final int AIRBORNE_TICKS = 4;

    private double walked;
    private int airborne;
    private boolean rightFoot;

    /**
     * Advances by a tick.
     *
     * @param dx       how far it moved since last tick, along X
     * @param dz       along Z
     * @param onGround whether it is on the ground now
     * @param stride   how far it travels between one foot coming down and the other
     */
    Step update(double dx, double dz, boolean onGround, double stride) {
        if (!onGround) {
            this.airborne++;
            return Step.NONE;
        }
        if (this.airborne >= AIRBORNE_TICKS) {
            this.airborne = 0;
            this.walked = 0D;
            return Step.BOTH;
        }
        this.airborne = 0;

        this.walked += Math.sqrt(dx * dx + dz * dz);
        if (this.walked < stride)
            return Step.NONE;
        // A tick that covers more than a stride (sprinting, riding a boat out) puts one foot down, not several
        this.walked = Math.min(this.walked - stride, stride);
        this.rightFoot = !this.rightFoot;
        return Step.ONE;
    }

    /**
     * Which foot the last single step put down
     */
    boolean rightFoot() {
        return this.rightFoot;
    }

    /**
     * Starts over, as if standing still on the ground: nothing walked, no landing to come
     */
    void reset() {
        this.walked = 0D;
        this.airborne = 0;
    }
}
