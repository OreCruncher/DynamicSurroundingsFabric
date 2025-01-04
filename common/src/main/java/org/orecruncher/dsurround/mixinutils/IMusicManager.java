package org.orecruncher.dsurround.mixinutils;

import net.minecraft.network.chat.Component;

public interface IMusicManager {

    String dsurround_getDiagnosticText();

    void dsurround_doCommand(String command);

    void dsurround_setPaused(boolean flag);

    Component dsurround_whatsPlaying();
}
