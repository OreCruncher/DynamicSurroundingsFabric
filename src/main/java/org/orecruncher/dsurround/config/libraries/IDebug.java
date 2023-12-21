package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public interface IDebug {

    Stream<String> dump();
}
