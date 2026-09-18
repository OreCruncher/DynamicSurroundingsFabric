package org.orecruncher.dsurround.mixins.core;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.world.entity.player.Abilities;
import org.orecruncher.dsurround.lib.music.DSurroundMusicManager;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {

    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At(value = "RETURN"))
    public void dsurround$createMusicManager(GameConfig gameConfig, CallbackInfo ci) {
        if (MixinHelpers.musicOptions.replaceMusicManager) {
            ReflectionHelper.cast(this, Minecraft.class)
                    .ifPresentOrElse(minecraft -> {
                                //noinspection ConstantConditions
                                if (minecraft.musicManager != null && !minecraft.musicManager.getClass().equals(MusicManager.class)) {
                                    MixinHelpers.LOGGER.warn("It looks like MusicManager was already replaced by '%s'. If this causes an issue disable Dynamic Surroundings music manager replacement in the configuration.".formatted(minecraft.musicManager.getClass().getName()));
                                }
                                minecraft.musicManager = new DSurroundMusicManager(minecraft);
                                MixinHelpers.LOGGER.info("Replaced Minecraft's MusicManager");
                            },
                            () -> MixinHelpers.LOGGER.warn("Unable to replace Minecraft's MusicManager"));
        } else {
            MixinHelpers.LOGGER.info("Not configured to replace MusicManager");
        }
    }

    @Unique
    private final Supplier<Abilities> dsurround$cachedAbilities = Suppliers.memoize(Abilities::new);

    /**
     * Hooks getting player abilities when checking whether to play situational music or the standard
     * creative Minecraft music when the player is in creative mode and in a dimension other than the Nether.
     * Substitute a fake Abilities instance to cause Minecraft to think the player is not in creative mode.
     *
     * Situational music is for playing music at in The End after a boss fight, while submerged underwater, or
     * if a biome has a background sound configured.
     */
    @WrapOperation(method = "getSituationalMusic()Lnet/minecraft/sounds/Music;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"))
    private Abilities dsurround$instabuildCheck(LocalPlayer instance, Operation<Abilities> original) {
        if (MixinHelpers.soundOptions.playBiomeMusicWhileCreative) {
            return this.dsurround$cachedAbilities.get();
        }
        return original.call(instance);
    }
}
