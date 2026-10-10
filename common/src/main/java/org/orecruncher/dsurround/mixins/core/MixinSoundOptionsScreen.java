package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.orecruncher.dsurround.gui.ComponentDisplayScreen;
import org.orecruncher.dsurround.gui.sound.IndividualSoundControlScreen;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.text.Localization;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.resources.FileResourceUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SoundOptionsScreen.class)
public abstract class MixinSoundOptionsScreen extends OptionsSubScreen {

    @Unique
    private static final Style dsurround$STYLE = Style.EMPTY.withColor(ColorPalette.GOLD);

    public MixinSoundOptionsScreen(Screen screen, Options options, Component component) {
        super(screen, options, component);
    }

    @Override
    protected void addFooter() {
        var configureToolTip = Tooltip.create(Component.translatable("dsurround.text.config.soundconfiguration.tooltip"));
        var configureText = Component.translatable("dsurround.text.config.soundconfiguration").withStyle(dsurround$STYLE);
        var configureWidth = GameUtils.getTextRenderer().width(configureText);

        var creditsToolTip = Tooltip.create(Component.translatable("dsurround.text.config.credits.tooltip"));
        var creditsText = Component.translatable("dsurround.text.config.credits").withStyle(dsurround$STYLE);
        var creditsWidth = GameUtils.getTextRenderer().width(creditsText);
        var standardWidth = Math.max(configureWidth, creditsWidth) + 10;

        var configureButton = Button.builder(configureText, this::dsurround$activateConfigScreen)
                .tooltip(configureToolTip)
                .width(standardWidth)
                .build();

        var creditsButton = Button.builder(creditsText, this::dsurround$activateCreditsScreen)
                .tooltip(creditsToolTip)
                .width(standardWidth)
                .build();

        var doneButton = Button.builder(CommonComponents.GUI_DONE, (arg) -> this.onClose()).width(standardWidth).build();

        var myLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(2), LayoutSettings::alignHorizontallyCenter);
        myLayout.addChild(configureButton);
        myLayout.addChild(doneButton);      // Keep the Done button in center of the footer
        myLayout.addChild(creditsButton);
    }

    @Unique
    private void dsurround$activateConfigScreen(Button button) {
        if (this.minecraft == null)
            return;

        // The screen pauses and resumes the music itself
        var enablePlayButtons = this.minecraft.level == null || GameUtils.isSinglePlayer();
        GameUtils.setScreen(new IndividualSoundControlScreen(this, enablePlayButtons));
    }

    @Unique
    private void dsurround$activateCreditsScreen(Button button) {
        String body = FileResourceUtil.readResourceFromJar("assets/CREDITS.md")
                .orElse(Localization.load("dsurround.text.config.resourcenotfound"));
        var screen = ComponentDisplayScreen.createFromMarkdown(this, Component.translatable("dsurround.text.config.credits.title").withStyle(dsurround$STYLE), body);
        GameUtils.setScreen(screen);
    }
}
