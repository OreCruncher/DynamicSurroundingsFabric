package org.orecruncher.dsurround.gui.sound;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.WarmToast;

public class SoundToast {

    public static void create(Music music) {
        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);
        var metadata = soundLibrary.getSoundMetadata(music.getEvent().value().getLocation());
        if (metadata != null && !metadata.getCredits().isEmpty()) {
            var title = metadata.getTitle();
            if (!Component.empty().equals(title)) {
                var author = metadata.getCredits().get(0).author();
                var titleLine = Component.translatable("dsurround.text.toast.music.title", title);
                var authorLine = Component.translatable("dsurround.text.toast.music.author", author);
                var toast = WarmToast.multiline(GameUtils.getMC(), titleLine, authorLine);
                GameUtils.getMC().getToasts().addToast(toast);
            }
        }
    }
}
