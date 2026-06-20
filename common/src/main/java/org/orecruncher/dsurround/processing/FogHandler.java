package org.orecruncher.dsurround.processing;

import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * No-op placeholder for the initial Minecraft 26.2 compatibility build.
 * The old FogRenderer hook targets classes that moved to the new fog renderer stack.
 */
public class FogHandler extends AbstractClientHandler {

    public FogHandler(Configuration config, IModLog logger) {
        super("Fog Handler", config, logger);
    }

    @Override
    public boolean doTick(final long tick) {
        return false;
    }
}
