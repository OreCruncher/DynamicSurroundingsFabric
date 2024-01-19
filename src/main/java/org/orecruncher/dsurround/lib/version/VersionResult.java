package org.orecruncher.dsurround.lib.version;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public record VersionResult(String version, String modId, String displayName, String downloadLocation, String downloadLocationModrinth, String releaseNotesLink) {

    public Component getChatText() {
        var space = Component.literal(" ");
        var openBracket = Component.literal("[").withStyle(Style.EMPTY.withColor(ColorPalette.SILVER_SAND));
        var closeBracket = Component.literal("]").withStyle(Style.EMPTY.withColor(ColorPalette.SILVER_SAND));

        var downloadPage = Component.translatable(this.modId + ".newversion.downloadpage")
                .withStyle(Style.EMPTY.withColor(ColorPalette.CORN_FLOWER_BLUE));
        var downloadHoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, downloadPage);

        var releaseNotesPage = Component.translatable(this.modId + ".newversion.releasenotespage")
                .withStyle(Style.EMPTY.withColor(ColorPalette.CORN_FLOWER_BLUE));
        var releaseNotesHoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, releaseNotesPage);

        var downloadStyleCurse = Style.EMPTY
                .withColor(ColorPalette.CURSEFORGE)
                .withHoverEvent(downloadHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocation));
        var curseHover = Component.translatable(this.modId + ".newversion.curseforge")
                .withStyle(downloadStyleCurse);

        var releaseNotesStyle = Style.EMPTY
                .withColor(ColorPalette.BRIGHT_CERULEAN)
                .withHoverEvent(releaseNotesHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.releaseNotesLink));
        var releaseNotesHover = Component.translatable(this.modId + ".newversion.releasenotes")
                .withStyle(releaseNotesStyle);

        var downloadStyleModrinth = Style.EMPTY
                .withColor(ColorPalette.MODRINTH)
                .withHoverEvent(downloadHoverEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocationModrinth));
        var modrinthHover = Component.translatable(this.modId + ".newversion.modrinth")
                .withStyle(downloadStyleModrinth);

        var modDisplayNameAndVersion = Component.literal(this.displayName)
                .append(" v").append(this.version)
                .withStyle(Style.EMPTY.withColor(ColorPalette.SUN_GLOW));

        return Component.translatable(this.modId + ".newversion.update")
                .withStyle(Style.EMPTY.withColor(ColorPalette.AQUAMARINE))
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
