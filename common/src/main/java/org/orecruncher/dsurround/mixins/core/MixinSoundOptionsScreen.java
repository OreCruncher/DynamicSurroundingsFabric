package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.mixinutils.IMusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundOptionsScreen.class)
public abstract class MixinSoundOptionsScreen extends OptionsSubScreen {

    public MixinSoundOptionsScreen(Screen screen, Options options, Component component) {
        super(screen, options, component);
    }

    @Inject(method = "addOptions()V", at = @At("RETURN"))
    public void dsurround_addSoundConfigButton(CallbackInfo ci) {
        // This will add a button in the lower left corner of the sound options menu
        var toolTip = Tooltip.create(Component.translatable("dsurround.text.config.soundconfiguration.tooltip"));
        var style = Style.EMPTY.withColor(ColorPalette.GOLD);
        var buttonText = Component.translatable("dsurround.text.config.soundconfiguration").withStyle(style);
        var textWidth = GameUtils.getTextRenderer().width(buttonText) + 10;

        var buttonToAdd = Button.builder(buttonText, this::dsurround_onPress)
                .tooltip(toolTip)
                .bounds(5, this.height - 27, textWidth, 20).build();

        this.layout.addToFooter(buttonToAdd, settings -> settings.alignHorizontally(0.01F));
    }

    @Unique
    private void dsurround_onPress(Button button) {
        var enablePlayButtons = GameUtils.getMC().level == null || GameUtils.isSinglePlayer();

        // If play buttons are enabled, we need to prevent the MusicManager from
        // ticking.
        var musicManager = (IMusicManager)GameUtils.getMC().getMusicManager();
        if (enablePlayButtons) {
            musicManager.dsurround_setPaused(true);
        }

        var screen = new IndividualSoundControlScreen(
                this,
                enablePlayButtons,
                ignore -> {
                    // Stop any sounds left hanging for whatever reason, and restart the MusicManager
                    GameUtils.getSoundManager().stop();
                    if (enablePlayButtons)
                        musicManager.dsurround_setPaused(false);
                });

        this.minecraft.setScreen(screen);
    }
}
