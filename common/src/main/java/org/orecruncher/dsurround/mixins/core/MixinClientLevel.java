// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.runtime.weather.RainSoundInstance;
import org.orecruncher.dsurround.runtime.weather.RainSurface;
import org.orecruncher.dsurround.runtime.weather.StormSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    /**
     * Tap into block state change detection in the World instance.  Need to be careful to only get updates to
     * a world that is client side.  Server side is a don't care.
     */
    @Inject(method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("RETURN"))
    public void dsurround$setBlocksDirty(BlockPos pos, BlockState old, BlockState current, int updateFlags, CallbackInfo ci) {
        BlockUpdateHandler.blockPositionUpdate((ClientLevel) (Object) this, pos, old, current);
    }

    /**
     * ClientChunkCache calls this once a chunk's data from the server has been loaded, so the chunk's blocks can
     * be read by the time it returns.
     */
    @Inject(method = "onChunkLoaded(Lnet/minecraft/world/level/ChunkPos;)V", at = @At("TAIL"))
    public void dsurround$onChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        IChunkLoad.EVENT.invoker().onChunkLoad((ClientLevel) (Object) this, chunkPos);
    }

    /**
     * Marks vanilla's rain being ticked, for the hooks on what it calls: the rain level (MixinLevel), its splashes and
     * its sound (below). The method is wrapped, rather than hooked inside: a mod that wraps it moves its body, and hooks
     * inside it are left with nothing to attach to (Iris does so on 1.21.11). The early order makes this the innermost
     * wrapper, next to the method's own code, as it has to run on the thread doing the tick: Async Particles' wrapper,
     * at the default order, hands the rest to a worker, and a mark made outside it would be gone by then.
     */
    @WrapMethod(method = "tickWeatherEffects()V", order = 500, require = 0)
    private void dsurround$rainTick(Operation<Void> original) {
        MixinHelpers.rainTickThread = Thread.currentThread();
        try {
            original.call();
        } finally {
            MixinHelpers.rainTickThread = null;
        }
    }

    /**
     * While particle rain is drawn, its drops splash where they land, so vanilla's splashes, scattered at random
     * around the player as its rain is ticked (see MixinHelpers.rainTickThread), are left out. Vanilla's rain sound,
     * played from the same tick, is kept.
     */
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$vanillaRainSplash(ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
        if (MixinHelpers.inRainTick() && MixinHelpers.RAIN_SPLASH_GUARD.get(() -> MixinHelpers.precipitation().isActive(), false))
            ci.cancel();
    }

    /**
     * The rain sound, as 1.12.2 had it: the mod's calm rain, louder as the storm strengthens, with a medium rain
     * through middling storms and vanilla's heavier rain blended in from a moderate storm up. Both of the rain tick's sounds, the rain in the open and on a roof above,
     * come through here (Level.playLocalSound at a BlockPos calls this); each keeps vanilla's volume and pitch for where
     * it is, scaled. Only while vanilla's rain is ticked (see MixinHelpers.rainTickThread). With rain on surfaces, each
     * sound sounds like the block it plays on (RainSurface): through its filter where enhanced sound processing can set
     * one, played as a RainSoundInstance that carries it, otherwise by volume and pitch alone.
     */
    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$rainSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        if (!MixinHelpers.inRainTick() || !MixinHelpers.weatherOptions.storms.enableStormSounds)
            return;
        var level = (ClientLevel) (Object) this;
        // Out of the rain tick meanwhile: the world's own rain level is read, and the sounds played here come back
        // through this method and play
        var thread = MixinHelpers.rainTickThread;
        MixinHelpers.rainTickThread = null;
        try {
            // The shares are worked out before anything plays, so a failure leaves vanilla's sound, played once
            float calmVolume;
            float medium;
            float heavy;
            SoundEvent calm;
            SoundEvent mediumRain;
            FixedFilter filter = null;
            // The surface's own rain and its volume, if it has any
            SoundEvent layer = null;
            float layerVolume = 0F;
            try {
                var rainLevel = MixinHelpers.weatherSimulation().rainLevel(level.getRainLevel(1F));
                calm = MixinHelpers.SOUND_LIBRARY.getSound(StormSounds.RAIN);
                mediumRain = MixinHelpers.SOUND_LIBRARY.getSound(StormSounds.RAIN_MEDIUM);
                // Vanilla plays it at the center of the block the rain lands on (see RainSurface)
                if (MixinHelpers.weatherOptions.storms.enableRainSurfaces) {
                    var surface = RainSurface.of(level, BlockPos.containing(x, y, z), MixinHelpers.TAG_LIBRARY);
                    MixinHelpers.RAIN_SURFACES.add(surface);
                    if (surface.filter() != null && EnhancedSoundProcessor.isAvailable())
                        filter = surface.filter();
                    volume *= surface.volume(filter != null);
                    pitch *= surface.pitch(filter != null);
                    // The surface's own rain takes part of the rain's place, at the surface's volume
                    if (surface.layer() != null) {
                        layer = MixinHelpers.SOUND_LIBRARY.getSound(surface.layer());
                        layerVolume = volume * StormSounds.layerShare(rainLevel);
                        volume *= StormSounds.keptShare();
                    }
                }
                calmVolume = volume * StormSounds.calmShare(rainLevel);
                medium = StormSounds.mediumShare(rainLevel);
                heavy = StormSounds.heavyShare(rainLevel);
            } catch (Throwable t) {
                MixinHelpers.RAIN_SOUND_GUARD.failed(t);
                return;
            }
            dsurround$playRain(level, x, y, z, calm, source, calmVolume, pitch, distanceDelay, filter);
            if (medium > 0F)
                dsurround$playRain(level, x, y, z, mediumRain, source, volume * medium, pitch, distanceDelay, filter);
            if (heavy > 0F)
                dsurround$playRain(level, x, y, z, sound, source, volume * heavy, pitch, distanceDelay, filter);
            // As recorded: neither filtered nor pitched, a roof's lower pitch included
            if (layer != null)
                dsurround$playRain(level, x, y, z, layer, source, layerVolume, 1F, distanceDelay, null);
            ci.cancel();
        } finally {
            MixinHelpers.rainTickThread = thread;
        }
    }

    /**
     * One layer of the rain sound: as vanilla plays it, or, with a filter, as vanilla makes it when nothing delays it
     * (rain is never delayed), but carrying the filter
     */
    @Unique
    private static void dsurround$playRain(ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source,
                                           float volume, float pitch, boolean distanceDelay, @Nullable FixedFilter filter) {
        if (filter == null || distanceDelay) {
            level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
            return;
        }
        var random = RandomSource.create(Randomizer.forThisThread().nextLong());
        Minecraft.getInstance().getSoundManager().play(new RainSoundInstance(sound, source, volume, pitch, random, x, y, z, filter));
    }

    /**
     * A lightning bolt arriving from the server: the mod draws it as a strike of its own, if its lightning is on (see
     * LightningHandler). Optional: without it the game draws its own bolts.
     */
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), require = 0)
    private void dsurround$lightningBolt(Entity entity, CallbackInfo ci) {
        if (entity instanceof LightningBolt bolt)
            MixinHelpers.LIGHTNING_GUARD.run(() -> MixinHelpers.lightning().onBolt(bolt));
    }
}
