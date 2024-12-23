package org.orecruncher.dsurround.gui.sound;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.WarmToast;

public class SoundToast {

    private static final WarmToast.Profile SOUND_TOAST_PROFILE = new WarmToast.Profile(ResourceLocation.withDefaultNamespace("toast/advancement"), 5000, ColorPalette.PUMPKIN_ORANGE, ColorPalette.WHEAT);

    public static void create(Music music) {
        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
        var metadata = soundLibrary.getSoundMetadata(music.getEvent().value().getLocation());
        if (metadata != null && !metadata.getCredits().isEmpty()) {
            var title = metadata.getTitle();
            if (!Component.empty().equals(title)) {
                var author = metadata.getCredits().get(0).author();
                var titleLine = Component.translatable("dsurround.text.toast.music.title", title);
                var authorLine = Component.translatable("dsurround.text.toast.music.author", author);
                var toast = WarmToast.multiline(GameUtils.getMC(), SOUND_TOAST_PROFILE, titleLine, authorLine);
                GameUtils.getMC().getToasts().addToast(toast);
            }
        }
    }
}
