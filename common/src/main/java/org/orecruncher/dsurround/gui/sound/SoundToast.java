package org.orecruncher.dsurround.gui.sound;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.gui.WarmToast;

public final class SoundToast {

    private static final WarmToast.Profile SOUND_TOAST_PROFILE = WarmToast.Profile.of(Identifier.withDefaultNamespace("toast/advancement"), 5000, ColorPalette.PUMPKIN_ORANGE, ColorPalette.WHEAT);

    public static void from(Music music) {
        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
        var metadata = soundLibrary.getSoundMetadata(music.sound().value().location());
        // getSoundMetadata never returns null; unknown sounds get default metadata with no title or credits
        if (metadata.hasTitle() && !metadata.getCredits().isEmpty()) {
            var author = metadata.getCredits().getFirst().author();
            var titleLine = Component.translatable("dsurround.text.toast.music.title", metadata.getTitle());
            var authorLine = Component.translatable("dsurround.text.toast.music.author", author);
            var toast = WarmToast.from(SOUND_TOAST_PROFILE, titleLine, authorLine);
            GameUtils.getToastManager().addToast(toast);
        }
    }
}
