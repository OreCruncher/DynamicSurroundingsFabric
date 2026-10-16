package org.orecruncher.dsurround.config;

import net.minecraft.network.chat.TextColor;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * The colors the light level overlay draws its numbers in
 */
public enum LightLevelColors {
    BRIGHT(ColorPalette.MC_GREEN, ColorPalette.MC_YELLOW, ColorPalette.MC_RED, ColorPalette.MC_AQUA),
    DARK(ColorPalette.MC_DARKGREEN, ColorPalette.MC_GOLD, ColorPalette.MC_DARKRED, ColorPalette.MC_DARKAQUA);

    private final int safe;
    private final int caution;
    private final int hazard;
    private final int noSpawn;

    LightLevelColors(TextColor safe, TextColor caution, TextColor hazard, TextColor noSpawn) {
        this.safe = safe.getValue();
        this.caution = caution.getValue();
        this.hazard = hazard.getValue();
        this.noSpawn = noSpawn.getValue();
    }

    /**
     * Bright enough that monsters can't spawn
     */
    public int safe() {
        return this.safe;
    }

    /**
     * Safe now, but monsters can spawn when the sky is darker (at night, in a storm)
     */
    public int caution() {
        return this.caution;
    }

    /**
     * Dark enough for monsters to spawn now
     */
    public int hazard() {
        return this.hazard;
    }

    /**
     * Monsters can't spawn on the block, however dark
     */
    public int noSpawn() {
        return this.noSpawn;
    }
}
