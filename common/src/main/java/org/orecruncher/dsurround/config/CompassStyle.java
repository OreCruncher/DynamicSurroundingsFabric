package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;

public enum CompassStyle {
    NONE,
    OPAQUE(0),
    TRANSPARENT(1),
    OPAQUE_WITH_INDICATOR(2),
    TRANSPARENT_WITH_INDICATOR(3),
    ROSE_1("textures/compass_rose_1.png"),
    ROSE_2("textures/compass_rose_2.png"),
    ROSE_3("textures/compass_rose_3.png");

    private final ResourceLocation texture;
    private final int spriteNumber;
    private final boolean isRose;

    /**
     * No compass display
     */
    CompassStyle() {
        this.texture = null;
        this.spriteNumber = 0;
        this.isRose = false;
    }

    /**
     * A band: one of the styles in the band texture
     */
    CompassStyle(int sprite) {
        this.texture = Constants.asId("textures/compass.png");
        this.spriteNumber = sprite;
        this.isRose = false;
    }

    /**
     * A rose: a whole texture of its own, laid flat and turned with the heading
     */
    CompassStyle(String texture) {
        this.texture = Constants.asId(texture);
        this.spriteNumber = 0;
        this.isRose = true;
    }

    /**
     * Whether the compass is displayed at all
     */
    public boolean isShown() {
        return this != NONE;
    }

    /**
     * The style's texture; null for NONE
     */
    public ResourceLocation getTexture() {
        return this.texture;
    }

    public int getSpriteNumber() {
        return this.spriteNumber;
    }

    public boolean isRose() {
        return this.isRose;
    }
}
