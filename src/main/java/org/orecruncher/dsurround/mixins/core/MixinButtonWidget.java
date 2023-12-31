package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ButtonWidget.class)
public interface MixinButtonWidget {

    @Accessor("onPress")
    ButtonWidget.PressAction dsurround_getPressAction();

    @Accessor("narrationSupplier")
    ButtonWidget.NarrationSupplier dsurround_getNarrationSupplier();
}
