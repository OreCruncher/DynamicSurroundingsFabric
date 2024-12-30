package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.lib.collections.ObjectArray;

public class AcousticEntryCollection extends ObjectArray<AcousticEntry> {

    @Override
    public boolean add(AcousticEntry entry) {
        if (this.contains(entry))
            return false;

        return super.add(entry);
    }
}
