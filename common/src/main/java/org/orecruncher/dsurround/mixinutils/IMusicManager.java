package org.orecruncher.dsurround.mixinutils;

import net.minecraft.network.chat.Component;

public interface IMusicManager {

    String dsurround$getDiagnosticText();

    void dsurround$doCommand(String command);

    void dsurround$setPaused(boolean flag);

    Component dsurround$whatsPlaying();
}
