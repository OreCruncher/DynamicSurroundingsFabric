package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.eventing.IChunkLoad;
import org.orecruncher.dsurround.eventing.handlers.BlockUpdateHandler;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.weather.StormSounds;
import org.spongepowered.asm.mixin.Mixin;
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
     * The storm's strength in place of the world's rain level where the rain is ticked: how many splashes, and the
     * rain sound. Drawn rain is in MixinWeatherEffectRenderer.
     */
    @ModifyExpressionValue(method = "tickWeatherEffects()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainLevelTicked(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }

    /**
     * While particle rain is drawn, its drops splash where they land, so vanilla's splashes, scattered at random
     * around the player, are left out. Vanilla's rain sound, played from the same method, is kept.
     */
    @WrapWithCondition(method = "tickWeatherEffects()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private boolean dsurround$vanillaRainSplash(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
        return MixinHelpers.RAIN_SPLASH_GUARD.get(() -> !MixinHelpers.precipitation().isActive(), true);
    }

    /**
     * The rain sound, as 1.12.2 had it: the mod's calm rain, louder as the storm strengthens, with vanilla's heavier
     * rain blended in from a moderate storm up. Both calls, the rain in the open and on a roof above, come through
     * here; each keeps vanilla's volume and pitch for where it is, scaled.
     */
    @WrapOperation(method = "tickWeatherEffects()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"), require = 0)
    private void dsurround$rainSound(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, Operation<Void> original) {
        if (!MixinHelpers.weatherOptions.enableStormSounds) {
            original.call(level, pos, sound, source, volume, pitch, distanceDelay);
            return;
        }
        // The shares are worked out before anything plays, so a failure leaves vanilla's sound, played once
        float calmVolume;
        float heavy;
        SoundEvent calm;
        try {
            var rainLevel = MixinHelpers.weatherSimulation().rainLevel(level.getRainLevel(1F));
            calm = MixinHelpers.SOUND_LIBRARY.getSound(StormSounds.RAIN);
            calmVolume = volume * StormSounds.calmShare(rainLevel);
            heavy = StormSounds.heavyShare(rainLevel);
        } catch (Throwable t) {
            MixinHelpers.RAIN_SOUND_GUARD.failed(t);
            original.call(level, pos, sound, source, volume, pitch, distanceDelay);
            return;
        }
        level.playLocalSound(pos, calm, source, calmVolume, pitch, distanceDelay);
        if (heavy > 0F)
            original.call(level, pos, sound, source, volume * heavy, pitch, distanceDelay);
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
