package org.orecruncher.dsurround.commands;

import net.minecraft.text.Text;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;

public class ReloadCommandHandler {

    public static Text execute() {
        try {
            AssetLibraryEvent.reload();
            return Text.translatable("dsurround.command.dsreload.success");
        } catch (Throwable t) {
            return Text.stringifiedTranslatable("dsurround.command.dsreload.failure", t.getMessage());
        }
    }
}
