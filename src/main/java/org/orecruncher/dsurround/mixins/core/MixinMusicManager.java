package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.mixinutils.IMusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MusicManager.class)
public class MixinMusicManager implements IMusicManager {
    @Shadow
    @Nullable
    private SoundInstance currentMusic;
    @Shadow
    private int nextSongDelay;

    @Override
    public String dsurround_getDiagnosticText() {
        String playingSound = "Nothing playing";
        if (this.currentMusic != null)
            playingSound = this.currentMusic.getLocation().toString();
        return "Music Manager: %d (%s)".formatted(this.nextSongDelay, playingSound);
    }

    @Override
    public void dsurround_reset() {
        MusicManager self = (MusicManager)(Object)this;
        self.stopPlaying();
        this.nextSongDelay = 100;
    }
}
