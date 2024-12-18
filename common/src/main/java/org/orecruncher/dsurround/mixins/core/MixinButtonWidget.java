package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Button.class)
public interface MixinButtonWidget {

    @Accessor("onPress")
    Button.OnPress dsurround_getPressAction();

    @Accessor("createNarration")
    Button.CreateNarration dsurround_getNarrationSupplier();
}
