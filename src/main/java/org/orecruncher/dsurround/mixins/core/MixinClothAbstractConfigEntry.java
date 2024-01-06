package org.orecruncher.dsurround.mixins.core;

import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(AbstractConfigEntry.class)
public class MixinClothAbstractConfigEntry {

    /**
     * @author OreCruncher
     * @reason Preserve style of Component.  The current implementation overrides color settings to force Gray.
     */
    @Overwrite
    public Component getDisplayedFieldName() {
        var self = (AbstractConfigEntry)((Object)this);
        MutableComponent text = self.getFieldName().copy();
        boolean hasError = self.getConfigError().isPresent();
        boolean isEdited = self.isEdited();

        if (!hasError && !isEdited) {
            // If the text entry does not have a color set, force
            // to gray.
            var color = text.getStyle().getColor();
            if (color == null)
                text = text.withStyle(ChatFormatting.GRAY);
        }

        if (hasError) {
            text = text.withStyle(ChatFormatting.RED);
        }

        if (isEdited) {
            text = text.withStyle(ChatFormatting.ITALIC);
        }

        if (!self.isEnabled()) {
            text = text.withStyle(ChatFormatting.DARK_GRAY);
        }

        return text;
    }

}
