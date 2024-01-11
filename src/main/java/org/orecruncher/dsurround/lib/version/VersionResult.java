package org.orecruncher.dsurround.lib.version;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public record VersionResult(String version, String modId, String displayName, String downloadLocation, String downloadLocationModrinth, String releaseNotesLink) {

    public Component getChatText() {
        var space = Component.literal(" ");
        var openBracket = Component.literal("[").withColor(ColorPalette.SILVER_SAND.getValue());
        var closeBracket = Component.literal("]").withColor(ColorPalette.SILVER_SAND.getValue());

        var downloadPage = Component.translatable(this.modId + ".newversion.downloadpage")
                .withColor(ColorPalette.CORN_FLOWER_BLUE.getValue());
        var downloadHoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, downloadPage);

        var releaseNotesPage = Component.translatable(this.modId + ".newversion.releasenotespage")
                .withColor(ColorPalette.CORN_FLOWER_BLUE.getValue());
        var releaseNotesHoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, releaseNotesPage);

        var downloadStyleCurse = Style.EMPTY
                .withHoverEvent(downloadHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocation));
        var curseHover = Component.translatable(this.modId + ".newversion.curseforge")
                .withColor(ColorPalette.CURSEFORGE.getValue())
                .withStyle(downloadStyleCurse);

        var releaseNotesStyle = Style.EMPTY
                .withHoverEvent(releaseNotesHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.releaseNotesLink));
        var releaseNotesHover = Component.translatable(this.modId + ".newversion.releasenotes")
                .withColor(ColorPalette.BRIGHT_CERULEAN.getValue())
                .withStyle(releaseNotesStyle);

        var downloadStyleModrinth = Style.EMPTY
                .withHoverEvent(downloadHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocationModrinth));
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
