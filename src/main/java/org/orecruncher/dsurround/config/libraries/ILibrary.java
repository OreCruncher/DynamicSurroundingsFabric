package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ILibrary extends IDebug {

    void reload(AssetLibraryEvent.ReloadEvent event);
}
