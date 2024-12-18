package org.orecruncher.dsurround.processing.scanner;

import org.orecruncher.dsurround.lib.di.Cacheable;

@Cacheable
public abstract class AbstractScanner {

    public abstract void tick(long tickCount);

}
