package org.orecruncher.dsurround.mixins.core;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.orecruncher.dsurround.eventing.ClientState;
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
        ReflectionHelper.cast(this, Minecraft.class)
                .ifPresent(minecraft -> minecraft.musicManager = new DSurroundMusicManager(minecraft));
    }

    @Unique
    private final Supplier<Abilities> dsurround$cachedAbilities = Suppliers.memoize(Abilities::new);

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void dsurround$tickStart(CallbackInfo info) {
        ReflectionHelper.cast(this, Minecraft.class)
                .ifPresent(minecraft -> ClientState.TICK_START.raise().onTickStart(minecraft));
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void dsurround$tickEnd(CallbackInfo info) {
        ReflectionHelper.cast(this, Minecraft.class)
                .ifPresent(minecraft -> ClientState.TICK_END.raise().onTickEnd(minecraft));
    }

    @Inject(method = "destroy()V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER, remap = false))
    private void dsurround$stopping(CallbackInfo ci) {
        ReflectionHelper.cast(this, Minecraft.class)
                .ifPresent(minecraft -> ClientState.STOPPING.raise().onStopping(minecraft));
    }

    @Inject(method = "run()V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0))
    private void dsurround$starting(CallbackInfo ci) {
        ReflectionHelper.cast(this, Minecraft.class)
                .ifPresent(minecraft -> ClientState.STARTED.raise().onStart(minecraft));
    }

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
