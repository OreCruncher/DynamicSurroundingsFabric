package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.Vector3d;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.mixins.MixinAbstractSoundInstance;

@Environment(EnvType.CLIENT)
public final class SoundFactory {

    private static final Identifier THUNDER = new Identifier(Client.ModId, "thunder");

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

    public static PositionedSoundInstance createAtLocation(SoundEvent event, BlockPos pos) {
        float x = pos.getX() + 0.5F;
        float y = pos.getY() + 0.5F;
        float z = pos.getZ() + 0.5F;
        return new PositionedSoundInstance(
                event,
                SoundCategory.AMBIENT,
                1F,
                1F,
                x,
                y,
                z
        );
    }

    public static PositionedSoundInstance cloneThunder(SoundInstance thunder) {
        MixinAbstractSoundInstance mixin = (MixinAbstractSoundInstance) thunder;
        return new PositionedSoundInstance(
                THUNDER,
                thunder.getCategory(),
                mixin.getRawVolume(),
                mixin.getRawPitch(),
                thunder.isRepeatable(),
                thunder.getRepeatDelay(),
                thunder.getAttenuationType(),
                thunder.getX(),
                thunder.getY(),
                thunder.getZ(),
                thunder.isRelative()
        );
    }

}
