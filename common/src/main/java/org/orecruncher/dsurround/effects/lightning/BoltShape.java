package org.orecruncher.dsurround.effects.lightning;

import java.util.List;

/**
 * The shape of a bolt of lightning: its main channel and the branches forking from it, each a line of points, in
 * blocks from where the bolt starts. Made by {@link BoltGenerator}; nothing here changes once made.
 */
public final class BoltShape {

    /**
     * One line of the bolt.
     *
     * @param points  x, y, z of each point in turn, from where it starts
     * @param arrival for each point, when the leader reaches it, as a share of the time it takes to cross the main
     *                channel: 0 where the bolt starts, 1 at the end of the main channel. A branch starts where its
     *                parent is reached, and may go on past 1.
     * @param depth   0 for the main channel, 1 for a branch from it, 2 for a branch from that, and so on
     */
    public record Channel(float[] points, float[] arrival, int depth) {

        public int size() {
            return this.arrival.length;
        }

        public float x(int i) {
            return this.points[i * 3];
        }

        public float y(int i) {
            return this.points[i * 3 + 1];
        }

        public float z(int i) {
            return this.points[i * 3 + 2];
        }
    }

    private final List<Channel> channels;

    BoltShape(List<Channel> channels) {
        if (channels.isEmpty())
            throw new IllegalArgumentException("a bolt needs a main channel");
        this.channels = List.copyOf(channels);
    }

    /**
     * Every channel, the main one first
     */
    public List<Channel> channels() {
        return this.channels;
    }

    public Channel main() {
        return this.channels.getFirst();
    }

    /**
     * The points in every channel
     */
    public int pointCount() {
        int count = 0;
        for (var channel : this.channels)
            count += channel.size();
        return count;
    }

    /**
     * The most branches deep it goes: 0 with no branches
     */
    public int depth() {
        int depth = 0;
        for (var channel : this.channels)
            depth = Math.max(depth, channel.depth());
        return depth;
    }
}
