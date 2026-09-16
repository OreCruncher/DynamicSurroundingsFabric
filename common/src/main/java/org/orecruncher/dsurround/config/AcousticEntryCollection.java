package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.weighted.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;
import java.util.stream.Stream;

public final class AcousticEntryCollection extends ObjectArray<AcousticEntry> {

    @Override
    public boolean add(AcousticEntry entry) {
        if (this.contains(entry))
            return false;
        return super.add(entry);
    }

    /**
     * Stream of AcousticEntries that match the current conditions within
     * the game.
     */
    public Stream<AcousticEntry> findMatches() {
        if (this.isEmpty())
            return Stream.empty();
        return this.stream().filter(AcousticEntry::matches);
    }

    /**
     * Makes a weighted choice from the candidates available in the
     * collection.
     */
    public Optional<ISoundFactory> makeSelection() {
        if (this.isEmpty())
            return Optional.empty();
        return WeightTable.makeSelection(this.findMatches(), Randomizer.current());
    }
}
