package org.orecruncher.dsurround.lib.version;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

public class VersionResult {
    public final String version;
    public final String displayName;
    public final String downloadLocation;
    public final String downloadLocationModrinth;

    VersionResult(String version, String displayName, String downloadLocation, String downloadLocationModrinth) {
        this.version = version;
        this.displayName = displayName;
        this.downloadLocation = downloadLocation;
        this.downloadLocationModrinth = downloadLocationModrinth;
    }

    public Component getChatText() {
        var openBracket = Component.empty().withColor(ColorPalette.SILVER_SAND.getValue()).append("[");
        var closeBracket = Component.empty().withColor(ColorPalette.SILVER_SAND.getValue()).append("]");

        var downloadPage = Component.empty()
                .withColor(ColorPalette.CORN_FLOWER_BLUE.getValue())
                .append(Component.translatable("dsurround.newversion.downloadpage"));
        var downloadEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, downloadPage);

        var downloadStyleCurse = Style.EMPTY
                .withHoverEvent(downloadEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocation));
        var curseHover = Component.empty()
                .withColor(ColorPalette.CURSEFORGE.getValue())
                .append(Component.translatable("dsurround.newversion.curseforge"))
                .withStyle(downloadStyleCurse);

        var downloadStyleModrinth = Style.EMPTY
                .withHoverEvent(downloadEvent)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, this.downloadLocationModrinth));
        var modrinthHover = Component.empty()
                .withColor(ColorPalette.MODRINTH.getValue())
                .append(Component.translatable("dsurround.newversion.modrinth"))
                .withStyle(downloadStyleModrinth);

        return Component.empty()
                .withColor(ColorPalette.AUQUAMARINE.getValue())
                .append(Component.translatable("dsurround.newversion.update"))
                .append(
                        Component.empty()
                                .withColor(ColorPalette.SUN_GLOW.getValue())
                                .append(ChatFormatting.stripFormatting(this.displayName))
                                .append(" v").append(this.version)
                )
                .append(Component.translatable("dsurround.newversion.at"))
                .append(openBracket)
                .append(curseHover)
                .append(closeBracket)
                .append(Component.translatable("dsurround.newversion.or"))
                .append(openBracket)
                .append(modrinthHover)
                .append(closeBracket);
    }
}
