package org.orecruncher.dsurround.lib.scanner;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

public class ClientPlayerContext extends ScanContext {

    public ClientPlayerContext() {
        super(
                GameUtils::getWorld,
                () -> GameUtils.getPlayer().getBlockPos(),
                () -> ContainerManager.resolve(IModLog.class),
                () -> GameUtils.getWorld().getRegistryKey().getValue()
        );
    }

}