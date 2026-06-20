package org.orecruncher.dsurround.processing.fog;

/**
 * Mod-local fog range data. The Minecraft 26.x fog renderer API was reworked; this keeps
 * the existing calculators compilable while the renderer hook is being reimplemented.
 */
public class FogData {
    public final String mode;
    public String shape = "default";
    public float start;
    public float end;

    public FogData(String mode) {
        this.mode = mode;
    }
}
