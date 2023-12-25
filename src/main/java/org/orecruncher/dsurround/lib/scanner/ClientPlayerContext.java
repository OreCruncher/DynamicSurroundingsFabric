package org.orecruncher.dsurround.lib.scanner;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;

public class ClientPlayerContext extends ScanContext {

    public ClientPlayerContext() {
        super(
                GameUtils::getWorld,
                () -> GameUtils.getPlayer().getBlockPos(),
                Library::getLogger,
                () -> GameUtils.getWorld().getRegistryKey().getValue()
        );
    }

}