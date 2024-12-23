package org.orecruncher.dsurround.config;

public enum CompassStyle {
    OPAQUE(0),
    TRANSPARENT(1),
    OPAQUE_WITH_INDICATOR(2),
    TRANSPARENT_WITH_INDICATOR(3);

    private final int spriteNumber;

    CompassStyle(int sprite) {
        this.spriteNumber = sprite;
    }

    public int getSpriteNumber() {
        return this.spriteNumber;
    }
}
