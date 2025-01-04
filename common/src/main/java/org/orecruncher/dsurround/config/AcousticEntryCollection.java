package org.orecruncher.dsurround.config;

import net.minecraft.util.random.SimpleWeightedRandomList;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class AcousticEntryCollection extends ObjectArray<AcousticEntry> {

    public static final AcousticEntryCollection EMPTY;

    static {
        EMPTY = new AcousticEntryCollection() {
            @Override
            public boolean add(AcousticEntry entry) {
                throw new RuntimeException("Cannot add AcousticEntry to EMPTY collection");
            }
            @Override
            public Stream<AcousticEntry> findMatches() {
                return Stream.empty();
            }
            @Override
            public Optional<ISoundFactory> makeSelection() {
                return Optional.empty();
            }
            @Override
            public SimpleWeightedRandomList<ISoundFactory> matchesAsWeightedList() {
                return SimpleWeightedRandomList.empty();
            }
        };
        EMPTY.trim();
    }

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
        return this.stream().filter(AcousticEntry::matches);
    }

    /**
     * Creates a SimpleWeightedRandomList based on valid candidates from within
     * the collection.
     */
    public SimpleWeightedRandomList<ISoundFactory> matchesAsWeightedList() {
        var builder = new SimpleWeightedRandomList.Builder<ISoundFactory>();
        this.findMatches().forEach(m -> builder.add(m.getAcoustic(), m.getWeight().asInt()));
        return builder.build();
    }

    /**
     * Makes a weighted choice from the candidates available in the
     * collection.
     */
    public Optional<ISoundFactory> makeSelection() {
        return WeightTable.makeSelection(this.findMatches(), Randomizer.current());
    }
}
