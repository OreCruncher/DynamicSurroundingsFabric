package org.orecruncher.dsurround.mixins.core;

import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a click on a Cloth Config group's name act on the text under the mouse, as Cloth's text entries do: the mod
 * ends its group names with a link to their documentation (see ClothAPIFactory). A click on anything without a click
 * event, which is every other mod's group names, expands or collapses the group as before. Only applied when Cloth
 * Config is installed (see DSurroundMixinPlugin); not required, so if Cloth changes the method the link just stops
 * working.
 */
@Mixin(targets = "me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry$CategoryLabelWidget")
public abstract class MixinClothCategoryLabelWidget {

    // Cloth draws the name this far right of the start of the header's area
    @Unique
    private static final int NAME_OFFSET = 15;

    @Shadow(remap = false)
    @Final
    private Rectangle rectangle;

    @Shadow(remap = false)
    @Final
    SubCategoryListEntry this$0;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$clickNameText(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0 || !this.rectangle.contains(event.x(), event.y()))
            return;
        var screen = this.this$0.getConfigScreen();
        if (screen == null)
            return;
        // The text under the mouse, tested the way vanilla tests clickable text; on the mouse's own line, so only
        // across the name does it matter where it is
        var font = Minecraft.getInstance().font;
        var finder = new ActiveTextCollector.ClickableStyleFinder(font, Mth.floor(event.x()), Mth.floor(event.y()));
        finder.accept(this.rectangle.x + NAME_OFFSET, Mth.floor(event.y()), this.this$0.getDisplayedFieldName());
        var style = finder.result();
        // The links are the documentation's, opened as vanilla opens links: asking first
        if (style != null && style.getClickEvent() instanceof ClickEvent.OpenUrl(var uri)) {
            ConfirmLinkScreen.confirmLinkNow(screen, uri);
            cir.setReturnValue(true);
        }
    }
}
