package org.orecruncher.dsurround.lib.collections;

/**
 * Keeps the {@code capacity} highest weighted of the keys offered to it, in a single pass with no sorting or
 * allocation: two small arrays, and the index of the lowest weight kept. While there is room every key is kept; once
 * full, a key gets in only by beating that lowest one, which it replaces.
 * <p>
 * {@link #wouldKeep} lets a caller skip expensive work for keys that wouldn't get in. Reusable: {@link #clear} it for
 * the next pass.
 */
public final class HighestWeights {

    private final long[] keys;
    private final double[] weights;
    private int count;
    private int lowest;

    public HighestWeights(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity must be at least 1: " + capacity);
        this.keys = new long[capacity];
        this.weights = new double[capacity];
    }

    public void clear() {
        this.count = 0;
        this.lowest = 0;
    }

    /**
     * Whether a key of this weight would be kept: always while there is room, otherwise only if it is heavier than
     * the lightest kept (a tie doesn't displace it).
     */
    public boolean wouldKeep(double weight) {
        return this.count < this.keys.length || weight > this.weights[this.lowest];
    }

    /**
     * Offers a key. Kept if {@link #wouldKeep} says so, replacing the lightest when full.
     *
     * @return true if it was kept
     */
    public boolean offer(long key, double weight) {
        if (!this.wouldKeep(weight))
            return false;
        if (this.count < this.keys.length) {
            this.keys[this.count] = key;
            this.weights[this.count] = weight;
            this.count++;
            if (this.count == this.keys.length)
                this.lowest = this.indexOfLowest();
        } else {
            this.keys[this.lowest] = key;
            this.weights[this.lowest] = weight;
            this.lowest = this.indexOfLowest();
        }
        return true;
    }

    /**
     * How many keys are kept.
     */
    public int size() {
        return this.count;
    }

    /**
     * A kept key, {@code 0 <= index < size()}, in no particular order.
     */
    public long key(int index) {
        if (index < 0 || index >= this.count)
            throw new IndexOutOfBoundsException(index);
        return this.keys[index];
    }

    private int indexOfLowest() {
        int result = 0;
        for (int i = 1; i < this.count; i++) {
            if (this.weights[i] < this.weights[result])
                result = i;
        }
        return result;
    }
}
