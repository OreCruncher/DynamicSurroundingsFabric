package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {

    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "RETURN"))
    public void dsurround$createMusicManager(GameConfig gameConfig, CallbackInfo ci) {
        if (MixinHelpers.musicOptions.replaceMusicManager) {
            var minecraft = (Minecraft) (Object) this;
            //noinspection ConstantConditions
            if (minecraft.musicManager != null && !minecraft.musicManager.getClass().equals(MusicManager.class)) {
                MixinHelpers.LOGGER.warn("It looks like MusicManager was already replaced by '%s'. If this causes an issue disable Dynamic Surroundings music manager replacement in the configuration.".formatted(minecraft.musicManager.getClass().getName()));
            }
            minecraft.musicManager = new DSurroundMusicManager(minecraft);
            MixinHelpers.LOGGER.info("Replaced Minecraft's MusicManager");
        } else {
            MixinHelpers.LOGGER.info("Not configured to replace MusicManager");
        }
    }

    /**
     * Hooks the choice of background music when Minecraft picks the situational music for the player in a world.
     * The game's background music (from the dimension and biome) has a track for each of default, creative and
     * underwater; music configured for the biome is chosen alongside the default one.
     * <p>
     * Underwater and creative music the game offers still play. With playBiomeMusicWhileCreative the player is not
     * treated as in creative, so the biome's music plays instead of the creative music.
     * <p>
     * The game's track is one of the choices only if the biome sets its own background music, as in 1.21.1. Otherwise,
     * the dimension's track (Musics.GAME in the overworld) plays only when the configured music gives nothing.
     */
    @WrapOperation(method = "getSituationalMusic()Lnet/minecraft/sounds/Music;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/attribute/BackgroundMusic;select(ZZ)Ljava/util/Optional;"))
    private Optional<Music> dsurround$biomeMusic(BackgroundMusic backgroundMusic, boolean isCreative, boolean isUnderwater, Operation<Optional<Music>> original) {
        if (MixinHelpers.soundOptions.playBiomeMusicWhileCreative)
            isCreative = false;

        var vanilla = original.call(backgroundMusic, isCreative, isUnderwater);
        if ((isUnderwater && backgroundMusic.underwaterMusic().isPresent()) || (isCreative && backgroundMusic.creativeMusic().isPresent()))
            return vanilla;

        // The background music is sampled at the camera, so the biome is too
        var minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null)
            return vanilla;
        var biome = minecraft.level.getBiome(minecraft.gameRenderer.mainCamera().blockPosition()).value();
        var info = MixinHelpers.biomeLibrary().findBiomeInfo(biome);
        if (info == null)
            return vanilla;

        var biomeTrack = biome.getAttributes().contains(EnvironmentAttributes.BACKGROUND_MUSIC) ? vanilla : Optional.<Music>empty();
        var chosen = info.getBackgroundMusic(biomeTrack, Randomizer.current());
        return chosen.isPresent() ? chosen : vanilla;
    }
}
