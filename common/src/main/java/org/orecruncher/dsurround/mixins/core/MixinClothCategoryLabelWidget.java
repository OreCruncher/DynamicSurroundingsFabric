package org.orecruncher.dsurround.mixins.core;

import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.math.Rectangle;
import net.minecraft.client.Minecraft;
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
    private void dsurround$clickNameText(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !this.rectangle.contains(mouseX, mouseY))
            return;
        var screen = this.this$0.getConfigScreen();
        if (screen == null)
            return;
        var name = this.this$0.getDisplayedFieldName();
        var style = Minecraft.getInstance().font.getSplitter()
                .componentStyleAtWidth(name, (int) mouseX - (this.rectangle.x + NAME_OFFSET));
        if (style != null && style.getClickEvent() != null && screen.handleComponentClicked(style))
            cir.setReturnValue(true);
    }
}
