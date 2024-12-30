package org.orecruncher.dsurround.mixinutils;

public interface IMusicManager {

    String dsurround_getDiagnosticText();

    void dsurround_doCommand(String command);

    void dsurround_setPaused(boolean flag);
}
