package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A queued piece of scanner work: a region whose blocks are to be scanned (they came into range) or unscanned
 * (they left range). The region is split into per-section boxes so the scanner can skip a whole box at once when
 * its section is empty or not loaded. A job can be paused partway and resumed on a later tick.
 */
final class ScanJob {

    private final boolean unscan;
    private final List<BlockBox> boxes;
    private int index = 0;
    private @Nullable CuboidPointIterator points;
    private long remaining;

    ScanJob(final boolean unscan, final BlockBox region) {
        this.unscan = unscan;
        this.boxes = Cuboid.splitBySection(region);
        this.remaining = Cuboid.volumeOf(region);
    }

    /**
     * True if the blocks are leaving range, false if they are coming into range.
     */
    boolean isUnscan() {
        return this.unscan;
    }

    boolean isDone() {
        return this.index >= this.boxes.size();
    }

    /**
     * The box being worked on, or null when the job is done. Every box lies within a single chunk section.
     */
    @Nullable
    BlockBox currentBox() {
        return this.isDone() ? null : this.boxes.get(this.index);
    }

    /**
     * The points of the current box, resuming where the last call left off.
     */
    CuboidPointIterator points() {
        if (this.points == null)
            this.points = new CuboidPointIterator(this.boxes.get(this.index));
        return this.points;
    }

    /**
     * Moves on to the next box, whether the current one was walked to the end or skipped.
     */
    void finishBox() {
        this.remaining -= Cuboid.volumeOf(this.boxes.get(this.index));
        this.index++;
        this.points = null;
    }

    /**
     * Blocks not yet processed. Counts whole boxes, so a partly walked box still counts in full.
     */
    long remaining() {
        return this.remaining;
    }
}
