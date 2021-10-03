package org.orecruncher.dsurround.gui.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ConfigSoundInstance extends PositionedSoundInstance {
    public ConfigSoundInstance(Identifier id, int volumeScale) {
        super(id, SoundCategory.AMBIENT, volumeScale / 100F, 1F, false, 0, SoundInstance.AttenuationType.NONE, 0.0D, 0.0D, 0.0D, true);
    }
}
