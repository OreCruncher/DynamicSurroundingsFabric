package org.orecruncher.dsurround.mixinutils;

import net.minecraft.network.chat.Component;

/**
 * Keeps the colour of Cloth Config field names. Cloth draws a field's name in gray when it is neither edited nor in
 * error, replacing any colour the name was given; the mod colours some of its option names, and this puts that
 * colour back. Names that are in error (red), edited (italic) or disabled (dark gray) are left as Cloth styles them.
 */
public final class ClothFieldNames {

    private ClothFieldNames() {
    }

    /**
     * The name to show.
     *
     * @param displayed what Cloth would show
     * @param fieldName the field's name, as given
     */
    public static Component keepColor(Component displayed, Component fieldName, boolean hasError, boolean isEdited, boolean isEnabled) {
        if (hasError || isEdited || !isEnabled)
            return displayed;
        var color = fieldName.getStyle().getColor();
        if (color == null)
            return displayed;
        return displayed.copy().withStyle(style -> style.withColor(color));
    }
}
