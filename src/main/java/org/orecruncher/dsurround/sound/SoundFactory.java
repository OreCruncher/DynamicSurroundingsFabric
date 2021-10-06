package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.Vector3d;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvent;
import org.orecruncher.dsurround.lib.math.MathStuff;

@Environment(EnvType.CLIENT)
public final class SoundFactory {

    public static BackgroundSoundLoop createBackgroundSoundLoop(SoundEvent event) {
        return new BackgroundSoundLoop(event);
    }

    public static PositionedSoundInstance createAsMood(SoundEvent event, Entity entity, int minRange, int maxRange) {
        final Vector3d offset = MathStuff.randomPoint(minRange, maxRange);
        final float posX = (float) (entity.getX() + offset.x);
        final float posY = (float) (entity.getEyeY() + offset.y);
        final float posZ = (float) (entity.getZ() + offset.z);
        return PositionedSoundInstance.ambient(event, posX, posY, posZ);
    }

    public static PositionedSoundInstance createAsAdditional(SoundEvent event) {
        return PositionedSoundInstance.ambient(event);
    }

}
