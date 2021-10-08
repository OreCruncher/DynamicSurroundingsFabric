package org.orecruncher.dsurround.lib.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;

@Environment(EnvType.CLIENT)
public class ClientPlayerContext extends ScanContext {

    public ClientPlayerContext() {
        super(
                GameUtils::getWorld,
                () -> GameUtils.getPlayer().getBlockPos(),
                () -> Client.LOGGER,
                () ->GameUtils.getWorld().getRegistryKey().getValue()
        );
    }

}