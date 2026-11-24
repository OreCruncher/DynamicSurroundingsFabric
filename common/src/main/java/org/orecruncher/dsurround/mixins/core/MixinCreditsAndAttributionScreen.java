// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.gui.credits.CreditsScreen;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a button named for the mod, in gold like its other buttons, under vanilla's Credits, Attribution and Licenses
 * buttons, opening the Attributions screen (as the Sound options' button does).
 */
@Mixin(CreditsAndAttributionScreen.class)
public abstract class MixinCreditsAndAttributionScreen extends Screen {

    @Shadow
    @Final
    private static int BUTTON_WIDTH;

    protected MixinCreditsAndAttributionScreen(Component title) {
        super(title);
    }

    // Vanilla's buttons are in place and the footer's Done comes next; the layout is arranged after that
    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToFooter(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private void dsurround$addCreditsButton(CallbackInfo ci, @Local LinearLayout buttons) {
        buttons.addChild(Button.builder(Component.translatable("dsurround.modname").withStyle(style -> style.withColor(ColorPalette.GOLD)), button -> CreditsScreen.open(this))
                .width(BUTTON_WIDTH)
                .build());
    }
}
