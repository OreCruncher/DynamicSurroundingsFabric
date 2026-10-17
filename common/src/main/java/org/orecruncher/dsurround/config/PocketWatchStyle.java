package org.orecruncher.dsurround.config;

import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.Constants;

/**
 * The look of the pocket watch. Each is an atlas of its own laid out the same way (see ClockOverlay), so only the
 * texture changes.
 */
public enum PocketWatchStyle {
    CLASSIC("textures/pocket_watch.png"),
    SQUARE("textures/pocket_watch_square.png");

    private final Identifier texture;

    PocketWatchStyle(String texture) {
        this.texture = Constants.asId(texture);
    }

    public Identifier getTexture() {
        return this.texture;
    }
}
