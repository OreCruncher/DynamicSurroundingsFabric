package org.orecruncher.dsurround.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.mixins.core.MixinAbstractSoundInstance;

@Environment(EnvType.CLIENT)
public final class SoundFactory {

    private static final Identifier THUNDER = new Identifier(Client.ModId, "thunder");

    private final SoundEvent soundEvent;
    private float minVolume = 1F;
    private float maxVolume = 1F;
    private float minPitch = 1F;
    private float maxPitch = 1F;
    private SoundCategory category = SoundCategory.AMBIENT;
    private boolean isRepeatable = false;
    private int repeatDelay = 0;
    private boolean global = false;
    private SoundInstance.AttenuationType attenuationType = SoundInstance.AttenuationType.LINEAR;
    private Vec3d position = Vec3d.ZERO;

    public SoundFactory(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
    }

    public SoundFactory volume(float vol) {
        this.minVolume = this.maxVolume = vol;
        return this;
    }

    public SoundFactory volumeRange(float min, float max) {
        this.minVolume = min;
        this.maxVolume = max;
        return this;
    }

    public SoundFactory pitch(float pitch) {
        this.minPitch = this.maxPitch = pitch;
        return this;
    }
    public SoundFactory pitchRange(float min, float max) {
        this.minPitch = min;
        this.maxPitch = max;
        return this;
    }

    public SoundFactory category(SoundCategory category) {
        this.category = category;
        return this;
    }

    public SoundFactory repeatable() {
        this.isRepeatable = true;
        return this;
    }

    public SoundFactory repeatable(int delay) {
        this.isRepeatable = true;
        this.repeatDelay = delay;
        return this;
    }

    public SoundFactory attenuation(SoundInstance.AttenuationType attenuation) {
        this.attenuationType = attenuation;
        this.global = attenuation == SoundInstance.AttenuationType.NONE;
        return this;
    }

    public SoundFactory global() {
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.global = true;
        return this;
    }

    private float generate(float min, float max) {
        var delta = max - min;
        if (Float.compare(delta, 0) == 0)
            return min;
        return (float) (XorShiftRandom.current().nextDouble() * delta + min);
    }

    private PositionedSoundInstance createInstance() {
        return new PositionedSoundInstance(
                this.soundEvent.getId(),
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                this.isRepeatable,
                this.repeatDelay,
                this.attenuationType,
                this.position.getX(),
                this.position.getY(),
                this.position.getZ(),
                this.global);
    }

    public BackgroundSoundLoop createBackgroundSoundLoop() {
        return new BackgroundSoundLoop(this.soundEvent)
                .setVolume(this.generate(this.minVolume, this.maxVolume))
                .setPitch(this.generate(this.minPitch, this.maxPitch));
    }

    public BackgroundSoundLoop createBackgroundSoundLoopAt(BlockPos pos) {
        return new BackgroundSoundLoop(this.soundEvent, pos)
                .setVolume(this.generate(this.minVolume, this.maxVolume))
                .setPitch(this.generate(this.minPitch, this.maxPitch));
    }

    public PositionedSoundInstance createAsMood(Entity entity, int minRange, int maxRange) {
        var offset = MathStuff.randomPoint(minRange, maxRange);
        this.position = entity.getEyePos().add(offset);
        return this.createInstance();
    }

    public PositionedSoundInstance createAsAdditional() {
        this.global();
        return this.createInstance();
    }

    public PositionedSoundInstance createAtLocation(BlockPos pos) {
        this.position = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        return this.createInstance();
    }

    public EntityTrackingSoundInstance createAtEntity(Entity entity) {
        return new EntityTrackingSoundInstance(
                this.soundEvent,
                this.category,
                this.generate(this.minVolume, this.maxVolume),
                this.generate(this.minPitch, this.maxPitch),
                entity
        );
    }

    public PositionedSoundInstance createAtLocation(Entity entity) {
        return createAtLocation(entity.getEyePos());
    }

    public PositionedSoundInstance createAtLocation(Vec3d position) {
        this.position = position;
        return this.createInstance();
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
