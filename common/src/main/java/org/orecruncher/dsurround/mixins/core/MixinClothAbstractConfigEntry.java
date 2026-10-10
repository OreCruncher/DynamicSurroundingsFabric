package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.mixinutils.ClothFieldNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the colour of option names in Cloth Config screens (see {@link ClothFieldNames}). Adjusts what Cloth returns
 * rather than replacing the method, so Cloth's own changes to it, and other mods', still apply. Only applied when
 * Cloth Config is installed (see DSurroundMixinPlugin).
 */
@Mixin(AbstractConfigEntry.class)
public abstract class MixinClothAbstractConfigEntry {

    @ModifyReturnValue(method = "getDisplayedFieldName", at = @At("RETURN"), remap = false)
    private Component dsurround$keepFieldNameColor(Component displayed) {
        var self = (AbstractConfigEntry<?>) (Object) this;
        return ClothFieldNames.keepColor(displayed, self.getFieldName(), self.getConfigError().isPresent(), self.isEdited(), self.isEnabled());
    }
}
