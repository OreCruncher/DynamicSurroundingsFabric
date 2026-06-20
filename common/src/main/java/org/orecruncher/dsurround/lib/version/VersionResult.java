package org.orecruncher.dsurround.lib.version;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public record VersionResult(String version, String modId, String displayName, String downloadLocation, String downloadLocationModrinth, String releaseNotesLink) {

    public Component getChatText() {
        var space = Component.literal(" ");
        var openBracket = Component.literal("[").withColor(ColorPalette.SILVER_SAND.getValue());
        var closeBracket = Component.literal("]").withColor(ColorPalette.SILVER_SAND.getValue());

        var downloadStyleCurse = Style.EMPTY.withUnderlined(true);
        var curseHover = Component.translatable(this.modId + ".newversion.curseforge")
                .withColor(ColorPalette.CURSEFORGE.getValue())
                .withStyle(downloadStyleCurse);

        var releaseNotesStyle = Style.EMPTY.withUnderlined(true);
        var releaseNotesHover = Component.translatable(this.modId + ".newversion.releasenotes")
                .withColor(ColorPalette.BRIGHT_CERULEAN.getValue())
                .withStyle(releaseNotesStyle);

        var downloadStyleModrinth = Style.EMPTY.withUnderlined(true);
        var modrinthHover = Component.translatable(this.modId + ".newversion.modrinth")
                .withColor(ColorPalette.MODRINTH.getValue())
                .withStyle(downloadStyleModrinth);

        var modDisplayNameAndVersion = Component.literal(this.displayName)
                .append(" v").append(this.version)
                .withColor(ColorPalette.SUN_GLOW.getValue());

        return Component.translatable(this.modId + ".newversion.update")
                .withColor(ColorPalette.AQUAMARINE.getValue())
                .append(modDisplayNameAndVersion)
                .append(space)
                .append(openBracket)
                .append(releaseNotesHover)
                .append(closeBracket)
                .append(space)
                .append(openBracket)
                .append(curseHover)
                .append(closeBracket)
                .append(space)
                .append(openBracket)
                .append(modrinthHover)
                .append(closeBracket);
    }
}
