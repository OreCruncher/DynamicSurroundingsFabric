package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public interface ISoundFactory {

    BackgroundSoundLoop createBackgroundSoundLoop();

    BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos);

    PositionedSoundInstance createAsMood(Entity entity, int minRange, int maxRange);

    PositionedSoundInstance createAsAdditional();

    PositionedSoundInstance createAtLocation(BlockPos pos);

    EntityTrackingSoundInstance createAtEntity(Entity entity);

    default PositionedSoundInstance createAtLocation(Entity entity) {
        return this.createAtLocation(entity.getEyePos());
    }

    PositionedSoundInstance createAtLocation(Vec3d position);

}
