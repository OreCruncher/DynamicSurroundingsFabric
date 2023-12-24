package org.orecruncher.dsurround.sound;

import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface ISoundFactory {

    /**
     * The underlying SoundEvent for the sound that is being produced by the factory
     */
    SoundEvent getSoundEvent();

    /**
     * Creates a sound instance that has no attenuation and that will repeat without any
     * delays.
     */
    BackgroundSoundLoop createBackgroundSoundLoop();

    /**
     * Creates a sound instance that has attenuation at the specified location.  The sound will
     * repeat without any delays.
     */
    BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos);

    /**
     * Creates an attenuated sound instance that will play at a location defined by the range
     * parameters around the specified entity player.  This sound instance will be tied to
     * a fixed location.  Mimics a biomes "mood" sound as defined in Minecraft.
     */

    PositionedSoundInstance createAsMood(Entity entity, int minRange, int maxRange);

    /**
     * Creates a non-attenuated sound instance that will not repeat.  Mimics the sound profile of
     * a biome "additional" sound as defined in Minecraft.
     */
    PositionedSoundInstance createAsAdditional();

    /**
     * Creates an attenuated sound instance at the center of the specified block location.  The
     * sound instance is not repeated.
     */
    PositionedSoundInstance createAtLocation(BlockPos pos);

    /**
     * Creates an attenuated sound instance that is attached to the entity and will move as
     * the entity moves.  The sound instance is not repeated.
     */
    EntityTrackingSoundInstance createAtEntity(Entity entity);

    /**
     * Creates a sound instance at the eye location of the specified entity.  This sound instance is not attached
     * to the entity and will be at a fixed location.  The properties of the sound instance are defined by the
     * underlying factory settings.
     */
    default PositionedSoundInstance createAtLocation(Entity entity) {
        return this.createAtLocation(entity.getEyePos());
    }

    /**
     * Creates a sound instance at the specified location.  This sound instance is not attached.  The properties
     * of the sound instance are defined by the underlying factory settings.
     */
    PositionedSoundInstance createAtLocation(Vec3d position);

}
