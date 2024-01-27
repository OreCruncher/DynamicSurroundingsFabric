package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class MixinSoundOptionsScreen extends Screen {

    protected MixinSoundOptionsScreen(Component component) {
        super(component);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    public void dsurround_addSoundConfigButton(CallbackInfo ci) {
        // This will add a button in the lower left corner of the sound options menu
        var toolTip = Tooltip.create(Component.translatable("dsurround.text.config.soundconfiguration.tooltip"));
        var style = Style.EMPTY.withColor(ColorPalette.GOLD);
        var buttonText = Component.translatable("dsurround.text.config.soundconfiguration").withStyle(style);
        var textWidth = GameUtils.getTextRenderer().width(buttonText) + 10;
        this.addRenderableWidget(Button.builder(buttonText, (button) -> {
                    var enablePlayButtons = GameUtils.getMC().level == null || GameUtils.isSinglePlayer();
                    var screen = new IndividualSoundControlScreen(this, enablePlayButtons);
                    this.minecraft.setScreen(screen);
                })
                .tooltip(toolTip)
                .bounds(5, this.height - 27, textWidth, 20).build());
    }
}
